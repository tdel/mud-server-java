package fr.idev.mudserver.game;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

import fr.idev.mudserver.domain.RoomInstance;
import fr.idev.mudserver.domain.WorldInstance;
import fr.idev.mudserver.domain.actor.GamePlayer;
import fr.idev.mudserver.domain.actor.event.CharacterDied;
import fr.idev.mudserver.domain.actor.event.GamePlayerDied;
import fr.idev.mudserver.domain.actor.event.GamePlayerMovedToRoom;
import fr.idev.mudserver.domain.actor.event.GamePlayerSpawnedToRoom;
import fr.idev.mudserver.game.actor.MonsterService;
import fr.idev.mudserver.network.message.ingame.GamePlayerDefeated;
import fr.idev.mudserver.network.message.ingame.MonsterDefeated;
import fr.idev.mudserver.persistence.CharacterDao;

/**
 * Réagit aux déplacements/morts en room — la matérialisation du graphe de
 * {@link RoomInstance} elle-même (depuis les {@code RoomTemplate} d'un
 * {@code WorldTemplate}) vit désormais dans {@link WorldInstanceService}, une
 * fois par {@code WorldInstance} plutôt qu'une seule fois pour tout le process
 * ; cette classe ne garde que les {@code @EventListener} qui répercutent les
 * mutations déjà appliquées en mémoire par {@link RoomInstance} lui-même
 * ({@code join}/{@code leave}/{@code broadcast}) vers la DB, exécutés via
 * {@code GamePlayer#moveToRoom}/{@code GamePlayer#spawnToRoom}.
 *
 * <p>
 * {@link #onGamePlayerMovedToRoom}/{@link #onGamePlayerSpawnedToRoom}
 * persistent {@code event.to().getTemplateId()}/{@code event.room()
 * .getTemplateId()} — l'id du {@code RoomTemplate} d'origine, pas celui de la
 * {@link RoomInstance} (calculé de façon déterministe par
 * {@link WorldInstanceService#materialize}, voir sa Javadoc) —
 * {@code character.current_room_id} désigne ainsi "quelle room du monde",
 * indépendamment de l'instance, résolue à nouveau contre la bonne
 * {@code WorldInstance} du personnage à chaque reconnexion
 * ({@link WorldInstanceService#spawnCharacterIntoInstance}).
 *
 * <p>
 * {@link #warmRooms()}/{@link #allRooms()}/{@link #startingRoom()}/
 * {@link #spawnCharacter(GamePlayer)} restent ici comme de simples délégations
 * vers {@link WorldInstanceService} (scopées à
 * {@link WorldInstance#DEFAULT_ID}, la seule instance qui existe tant que le
 * Lobby/Party n'existe pas) — uniquement pour ne pas casser la vaste surface de
 * tests qui appelle {@code roomService.warmRooms()} comme point d'entrée unique
 * de bootstrap, exactement comme au moment de son introduction en Phase A (voir
 * son ancienne Javadoc).
 */
@Service
public class RoomService {

    private static final Logger log = LoggerFactory.getLogger(RoomService.class);

    private final WorldInstanceService worldInstanceService;
    private final WorldTemplateService worldTemplateService;
    private final ItemService itemService;
    private final MonsterService monsterService;
    private final CharacterDao characterDao;

    public RoomService(WorldInstanceService worldInstanceService, WorldTemplateService worldTemplateService,
            ItemService itemService, MonsterService monsterService, CharacterDao characterDao) {
        this.worldInstanceService = worldInstanceService;
        this.worldTemplateService = worldTemplateService;
        this.itemService = itemService;
        this.monsterService = monsterService;
        this.characterDao = characterDao;
    }

    /**
     * Déclenche elle-même {@code itemService.warmItemTemplates()},
     * {@code worldTemplateService.warmWorldTemplates(...)} puis
     * {@code monsterService.warmMonsterTemplates(...)} avant de matérialiser
     * l'instance par défaut (monstres/PNJ/items compris, voir
     * {@code WorldInstanceService.materialize}) — même raison qu'en Phase A (voir
     * historique) : les catalogues boutique des PNJ ont besoin des templates
     * d'items déjà chargés, et le placement des monstres a besoin de leurs
     * templates déjà chargés. Garde ainsi {@code warmRooms()} auto-suffisant pour
     * tous les appelants existants (tests notamment).
     */
    public void warmRooms() {
        itemService.warmItemTemplates();
        worldTemplateService.warmWorldTemplates(itemService.templateSummariesById());
        monsterService.warmMonsterTemplates(itemService.templateIds());
        worldInstanceService.warmDefaultInstance();
    }

    public Collection<RoomInstance> allRooms() {
        return worldInstanceService.getOrMaterialize(WorldInstance.DEFAULT_ID).roomInstances();
    }

    public Optional<RoomInstance> startingRoom() {
        return worldInstanceService.getOrMaterialize(WorldInstance.DEFAULT_ID).startingRoomInstance();
    }

    public void spawnCharacter(GamePlayer character) {
        UUID worldInstanceId = character.getWorldInstanceId() != null
                ? character.getWorldInstanceId()
                : WorldInstance.DEFAULT_ID;
        worldInstanceService.spawnCharacterIntoInstance(character,
                worldInstanceService.getOrMaterialize(worldInstanceId));
    }

    @EventListener
    void onGamePlayerMovedToRoom(GamePlayerMovedToRoom event) {
        event.character().setCurrentRoomId(event.to().getTemplateId());
        characterDao.updateCurrentRoom(event.character().getId(), event.to().getTemplateId());
        log.debug("room.player_moved character={} to={}", event.character().getName(), event.to().getName());
    }

    @EventListener
    void onGamePlayerSpawnedToRoom(GamePlayerSpawnedToRoom event) {
        event.character().setCurrentRoomId(event.room().getTemplateId());
        characterDao.updateCurrentRoom(event.character().getId(), event.room().getTemplateId());
        log.info("room.player_spawned character={} room={}", event.character().getName(), event.room().getName());
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
        RoomInstance room = event.character().getCurrentRoom();
        room.removeMonster(event.character());
        room.broadcast(new MonsterDefeated(event.character().getName()), null);
        log.info("combat.monster_removed_from_room monster={} room={}", event.character().getName(), room.getName());
    }

    /**
     * {@code @Order(1)} : doit s'exécuter avant que
     * {@code CharacterService#onGamePlayerDied} ne téléporte le mourant hors de
     * cette room — sinon {@code event.character().getCurrentRoom()} pointerait déjà
     * vers la starting room. Le mourant est exclu du broadcast : il reçoit son
     * propre message ({@code PlayerRespawned}) via {@code CharacterService}.
     */
    @EventListener
    @Order(1)
    void onGamePlayerDied(GamePlayerDied event) {
        RoomInstance room = event.character().getCurrentRoom();
        room.broadcast(new GamePlayerDefeated(event.character().getName(), event.killer().getName()),
                event.character());
        log.info("combat.player_defeated character={} killer={} room={}", event.character().getName(),
                event.killer().getName(), room.getName());
    }
}
