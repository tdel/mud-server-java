package app.domain.world;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import app.domain.Account;
import app.domain.actor.Attribute;
import app.domain.actor.CharacterClass;
import app.domain.actor.instance.CharacterInstance;
import app.domain.actor.Gender;
import app.domain.actor.Race;
import app.domain.actor.event.DomainEventPublisher;
import app.domain.actor.event.NewGamePlayerCreated;
import app.domain.actor.event.PlayerLoadedInWorld;
import app.domain.actor.event.PlayerRemovedFromWorld;
import app.domain.map.Position;

public class WorldInstance {

    private static final Logger log = LoggerFactory.getLogger(WorldInstance.class);

    public static final UUID DEFAULT_ID = UUID.fromString("a8e98a8e-73c1-43dd-b36e-a2f67f00ff48");

    private final UUID id;
    private final UUID worldTemplateId;
    private final Instant createdAt;

    private Map<UUID, MapInstance> mapInstances = Map.of();

    private final Map<UUID, CharacterInstance> players = new ConcurrentHashMap<>();

    public WorldInstance(UUID id, UUID worldTemplateId, Instant createdAt) {
        this.id = id;
        this.worldTemplateId = worldTemplateId;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getWorldTemplateId() {
        return worldTemplateId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setMapInstances(Map<UUID, MapInstance> mapInstances) {
        this.mapInstances = Map.copyOf(mapInstances);
    }

    public Collection<MapInstance> mapInstances() {
        return mapInstances.values();
    }

    public Optional<MapInstance> mapInstanceForTemplate(UUID mapTemplateId) {
        return Optional.ofNullable(mapInstances.get(mapTemplateId));
    }

    public Optional<MapInstance> startingMapInstance() {
        return mapInstances.values().stream().filter(map -> Boolean.TRUE.equals(map.isStartingMap())).findFirst();
    }

    // putIfAbsent avant tout join sur la map : ferme le TOCTOU d'un personnage
    // chargé deux fois
    // (ex. reconnexion après un crash réseau dont le cleanup n'a pas encore tourné)
    // — un rejet ne
    // doit laisser aucune trace sur la map, donc le check précède le join, jamais
    // l'inverse.
    //
    // Séparé de joinWorld() (2026-09-05) : la sélection d'un personnage existant
    // (CharacterSelect) a besoin de réserver le slot AVANT d'attacher la connexion
    // (pour garder cette protection anti double-login) mais de ne rejoindre la map
    // (et donc déclencher KnownList.populate()/EntityAppeared) qu'APRÈS l'avoir
    // attachée, sous peine que ce message parte vers une connexion encore nulle et
    // soit perdu silencieusement (CharacterInstance.send() no-op si connection ==
    // null).
    public boolean reservePlayer(CharacterInstance character) {
        if (players.putIfAbsent(character.getId(), character) != null) {
            log.warn("world.player_already_loaded worldId={} character={}", id, character.getId());
            return false;
        }
        return true;
    }

    public void joinWorld(CharacterInstance character) {
        Position savedPosition = character.getMotionSystem().getPosition();
        if (savedPosition != null) {
            character.getMotionSystem().getCurrentMap().join(character, savedPosition);
        } else {
            character.getMotionSystem().getCurrentMap().join(character);
        }
        log.info("world.player_loaded thread={} worldId={} character={}", Thread.currentThread().getName(), id,
                character.getId());
        DomainEventPublisher.publish(new PlayerLoadedInWorld(character));
    }

    public boolean loadPlayer(CharacterInstance character) {
        if (!reservePlayer(character)) {
            return false;
        }
        joinWorld(character);
        return true;
    }

    public void removePlayer(CharacterInstance character) {
        players.remove(character.getId());
        log.info("world.player_removed thread={} worldId={} character={}", Thread.currentThread().getName(), id,
                character.getId());
        DomainEventPublisher.publish(new PlayerRemovedFromWorld(character));
    }

    public Collection<CharacterInstance> onlineCharacters() {
        return List.copyOf(players.values());
    }

    public boolean isCharacterInGame(UUID characterId) {
        return players.containsKey(characterId);
    }

    public CharacterInstance createCharacter(Account account, String name, Gender gender, Race race,
            CharacterClass characterClass) {
        MapInstance startingMap = startingMapInstance()
                .orElseThrow(() -> new IllegalStateException("WorldInstance " + id + " n'a aucune map de départ"));

        Map<Attribute, Integer> scores = characterClass.baseAttributes();

        int maxHealth = characterClass.maxHealth(scores.get(Attribute.CON), 1);

        int startingMana = characterClass.maxMana(scores.get(Attribute.MEN), 1);

        CharacterInstance character = new CharacterInstance(UUID.randomUUID(), account, name, startingMap, gender, race,
                characterClass, 1, maxHealth, maxHealth, scores, 0, 0, startingMana, startingMana, Map.of(), List.of(),
                List.of(), Map.of(), List.of(), null, null, 0, 0, 0, false);
        character.setWorldInstance(this);

        DomainEventPublisher.publish(new NewGamePlayerCreated(character));

        return character;
    }

    @Override
    public String toString() {
        return "WorldInstance[id=" + id + ", worldTemplateId=" + worldTemplateId + ", createdAt=" + createdAt + "]";
    }
}
