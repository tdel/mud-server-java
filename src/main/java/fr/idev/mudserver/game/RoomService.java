package fr.idev.mudserver.game;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

import fr.idev.mudserver.domain.actor.GamePlayer;
import fr.idev.mudserver.domain.Room;
import fr.idev.mudserver.domain.RoomExit;
import fr.idev.mudserver.domain.actor.event.CharacterDied;
import fr.idev.mudserver.domain.actor.event.GamePlayerMovedToRoom;
import fr.idev.mudserver.domain.actor.event.GamePlayerSpawnedToRoom;
import fr.idev.mudserver.network.message.ingame.MonsterDefeated;
import fr.idev.mudserver.persistence.CharacterDao;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Point d'entrée unique pour le cache des rooms. {@code Room} (depuis la fusion
 * de RoomInstance, voir historique) reste à la fois l'entité chargée et le
 * conteneur runtime des personnages présents ; {@code RoomService} est la
 * couche cache/cycle de vie au-dessus (warm/lookup) — les mutations
 * d'appartenance ({@code join}/{@code leave}/{@code disconnect}/
 * {@code broadcast}) restent portées par {@link Room} lui-même, appelées via
 * {@code GamePlayer#moveToRoom}/{@link #spawnCharacter}. Précharge rooms et
 * exits en une seule passe depuis {@code data/rooms.json} (voir
 * {@link #warmRooms()}), sur le même principe que
 * {@code ItemService.warmItemTemplates()} : donnée de contenu statique, jamais
 * mutée en jeu, chargée depuis le classpath plutôt que la DB. Garde malgré tout
 * une dépendance à {@link CharacterDao} : contrairement aux rooms,
 * {@code character.current_room_id} reste une colonne DB mutable en jeu —
 * {@link #onGamePlayerMovedToRoom}/{@link #onGamePlayerSpawnedToRoom} la
 * répercutent à chaque déplacement. {@link #onCharacterDied} retire le monstre
 * mort de sa room et diffuse {@code MonsterDefeated} — la mort elle-même
 * (détection du coup fatal, crédit d'XP) reste hors du périmètre de cette
 * classe, voir {@code CombatService}/{@code CharacterService}. Pas d'accesseur
 * générique {@code room(UUID)} : en dehors du warm-up/des tests, tout code
 * applicatif doit passer par une méthode qui exprime une intention métier
 * ({@link #spawnCharacter}), jamais par une résolution d'UUID brute.
 */
@Service
public class RoomService {

    private static final String ROOMS_RESOURCE = "/data/rooms.json";

    private final Map<UUID, Room> rooms = new ConcurrentHashMap<>();

    private final ObjectMapper objectMapper;
    private final CharacterDao characterDao;

    public RoomService(ObjectMapper objectMapper, CharacterDao characterDao) {
        this.objectMapper = objectMapper;
        this.characterDao = characterDao;
    }

    public void warmRooms() {
        try (InputStream in = getClass().getResourceAsStream(ROOMS_RESOURCE)) {
            List<RoomDefinition> definitions = objectMapper.readValue(in, new TypeReference<List<RoomDefinition>>() {
            });
            loadRooms(definitions);
        } catch (IOException | JacksonException e) {
            throw new IllegalStateException("Impossible de charger " + ROOMS_RESOURCE, e);
        }
    }

    void loadRooms(List<RoomDefinition> definitions) {
        long startingRoomCount = definitions.stream()
                .filter(definition -> Boolean.TRUE.equals(definition.isStartingRoom())).count();
        if (startingRoomCount > 1) {
            throw new IllegalStateException(
                    "Plusieurs rooms marquées isStartingRoom dans " + ROOMS_RESOURCE + " : une seule autorisée");
        }

        for (RoomDefinition definition : definitions) {
            Room room = new Room(definition.id(), definition.name(), definition.description(),
                    definition.isStartingRoom());
            rooms.put(room.getId(), room);
        }

        for (RoomDefinition definition : definitions) {
            Room source = rooms.get(definition.id());
            List<RoomExit> exits = definition.exits().stream().map(exit -> {
                Room target = rooms.get(exit.targetRoomId());
                if (target == null) {
                    throw new IllegalStateException("Room " + definition.id() + " a un exit '" + exit.direction()
                            + "' vers " + exit.targetRoomId() + ", absente de " + ROOMS_RESOURCE);
                }
                return new RoomExit(exit.direction(), source, target);
            }).toList();
            source.setExits(exits);
        }
    }

    public void spawnCharacter(GamePlayer character) {
        character.spawnToRoom(rooms.get(character.getCurrentRoomId()));
    }

    public Collection<Room> allRooms() {
        return rooms.values();
    }

    public Optional<Room> startingRoom() {
        return rooms.values().stream().filter(room -> Boolean.TRUE.equals(room.isStartingRoom())).findFirst();
    }

    @EventListener
    void onGamePlayerMovedToRoom(GamePlayerMovedToRoom event) {
        event.character().setCurrentRoomId(event.to().getId());
        characterDao.updateCurrentRoom(event.character().getId(), event.to().getId());
    }

    @EventListener
    void onGamePlayerSpawnedToRoom(GamePlayerSpawnedToRoom event) {
        event.character().setCurrentRoomId(event.room().getId());
        characterDao.updateCurrentRoom(event.character().getId(), event.room().getId());
    }

    /**
     * {@code @Order(1)} : ce listener doit diffuser {@code MonsterDefeated} avant
     * que {@code CharacterService#onCharacterDied} ne déclenche le crédit d'XP (et
     * une éventuelle montée de niveau) sur ce même événement — l'ordre des messages
     * reçus par le joueur (mort du monstre avant XP/niveau) en dépend. Diffusé à
     * toute la room sans exclusion : le tueur reçoit lui-même ce message comme tout
     * le monde.
     */
    @EventListener
    @Order(1)
    void onCharacterDied(CharacterDied event) {
        Room room = event.character().getCurrentRoom();
        room.removeMonster(event.character());
        room.broadcast(new MonsterDefeated(event.character().getName()), null);
    }

    record RoomDefinition(UUID id, String name, String description, Boolean isStartingRoom,
            List<ExitDefinition> exits) {
    }

    record ExitDefinition(String direction, UUID targetRoomId) {
    }
}
