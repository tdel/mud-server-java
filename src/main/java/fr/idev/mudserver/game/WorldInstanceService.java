package fr.idev.mudserver.game;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import fr.idev.mudserver.domain.RoomInstance;
import fr.idev.mudserver.domain.RoomPortal;
import fr.idev.mudserver.domain.RoomTemplate;
import fr.idev.mudserver.domain.RoomTemplatePortal;
import fr.idev.mudserver.domain.WorldInstance;
import fr.idev.mudserver.domain.WorldTemplate;
import fr.idev.mudserver.domain.actor.GamePlayer;
import fr.idev.mudserver.domain.actor.event.DomainEventPublisher;
import fr.idev.mudserver.domain.actor.event.WorldInstanceCreated;
import fr.idev.mudserver.game.actor.MonsterService;
import fr.idev.mudserver.game.actor.NpcService;
import fr.idev.mudserver.network.OutputMessage;
import fr.idev.mudserver.persistence.WorldInstanceDao;

/**
 * Matérialise le graphe runtime de {@link RoomInstance} d'une
 * {@link WorldInstance} à partir de son {@link WorldTemplate} — monstres, PNJ
 * et items au sol inclus — et résout "quelle room" un personnage doit rejoindre
 * en son sein. Résidente en mémoire à la demande, pas toutes au démarrage :
 * {@code ServerApplication.warmupRunner} ne charge plus que les catalogues
 * globaux (templates d'items/monstres, {@code WorldTemplate} eux-mêmes) ; le
 * contenu runtime d'une instance donnée (cette classe) n'est matérialisé que la
 * première fois qu'un joueur y entre réellement, via {@link #getOrMaterialize}
 * ({@code WorldEnter}, repli solo) ou {@link #createInstance}
 * ({@code WorldEnter}, lancement de party) — {@code warmDefaultInstance()}
 * reste le point d'entrée utilisé par les tests comme bootstrap unique pour
 * l'instance par défaut.
 *
 * <p>
 * {@link RoomInstance#deterministicId} donne à chaque {@link RoomInstance} un
 * id stable d'un redémarrage à l'autre (calculé, pas généré), ce qui permet à
 * {@code character.current_room_id} de rester un id de {@code RoomTemplate}
 * (indépendant de l'instance, voir {@code RoomService}) tout en retrouvant
 * toujours la même {@link RoomInstance} en mémoire après une
 * (re)matérialisation.
 */
@Service
public class WorldInstanceService {

    private static final Logger log = LoggerFactory.getLogger(WorldInstanceService.class);

    private final Map<UUID, WorldInstance> residentInstances = new ConcurrentHashMap<>();

    private final WorldTemplateService worldTemplateService;
    private final WorldInstanceDao worldInstanceDao;
    private final MonsterService monsterService;
    private final NpcService npcService;
    private final ItemService itemService;

    public WorldInstanceService(WorldTemplateService worldTemplateService, WorldInstanceDao worldInstanceDao,
            MonsterService monsterService, NpcService npcService, ItemService itemService) {
        this.worldTemplateService = worldTemplateService;
        this.worldInstanceDao = worldInstanceDao;
        this.monsterService = monsterService;
        this.npcService = npcService;
        this.itemService = itemService;
    }

    /**
     * Charge et (re)matérialise l'unique {@code WorldInstance} par défaut au
     * démarrage — appelée depuis {@code RoomService.warmRooms()}, elle-même appelée
     * aussi bien par {@code ServerApplication.warmupRunner} que par la vaste
     * majorité des tests comme point d'entrée de bootstrap. Contrairement à
     * {@link #getOrMaterialize}, force toujours une reconstruction complète (retire
     * d'abord l'entrée résidente, voir {@link #materialize}) plutôt que de renvoyer
     * une instance déjà en cache : un contexte Spring de test est mis en cache et
     * partagé entre classes de test, chacune appelant {@code warmRooms()} en
     * s'attendant à un graphe de {@link RoomInstance} entièrement neuf (occupants
     * vides), pas à celui laissé par la classe de test précédente.
     */
    public WorldInstance warmDefaultInstance() {
        residentInstances.remove(WorldInstance.DEFAULT_ID);
        return getOrMaterialize(WorldInstance.DEFAULT_ID);
    }

    public WorldInstance getOrMaterialize(UUID worldInstanceId) {
        WorldInstance resident = residentInstances.get(worldInstanceId);
        if (resident != null) {
            return resident;
        }
        WorldInstance instance = worldInstanceDao.findById(worldInstanceId)
                .orElseThrow(() -> new IllegalStateException("WorldInstance " + worldInstanceId + " absente en base"));
        return materialize(instance);
    }

    /**
     * Deux passes pour le graphe de rooms, comme l'ancien
     * {@code RoomService.loadRooms} : la première construit toutes les
     * {@link RoomInstance} (id déterministe, config statique copiée depuis leur
     * {@link RoomTemplate}), la seconde résout les {@link RoomTemplatePortal} en
     * références d'objet {@link RoomPortal} — les deux rooms d'un portail doivent
     * déjà exister en mémoire avant de pouvoir se référencer l'une l'autre. Une
     * fois le graphe construit, place aussi son contenu runtime (monstres, PNJ,
     * items au sol depuis la DB) — cette instance n'est donc réellement prête pour
     * des joueurs qu'à la sortie de cette méthode, jamais avant. Le garde de
     * résidence ci-dessus garantit que ce placement ne tourne qu'une fois par
     * {@code WorldInstance}, jamais deux fois pour la même instance.
     */
    public WorldInstance materialize(WorldInstance instance) {
        WorldInstance resident = residentInstances.get(instance.getId());
        if (resident != null) {
            return resident;
        }

        WorldTemplate template = worldTemplateService.findById(instance.getWorldTemplateId())
                .orElseThrow(() -> new IllegalStateException("WorldTemplate " + instance.getWorldTemplateId()
                        + " absent, requis par WorldInstance " + instance.getId()));

        Map<UUID, RoomInstance> roomInstances = new LinkedHashMap<>();
        for (RoomTemplate roomTemplate : template.getRoomTemplates().values()) {
            UUID roomInstanceId = RoomInstance.deterministicId(instance.getId(), roomTemplate.getId());
            RoomInstance room = new RoomInstance(roomInstanceId, roomTemplate.getName(), roomTemplate.getDescription(),
                    roomTemplate.isStartingRoom(), roomTemplate.getWidth(), roomTemplate.getHeight(),
                    roomTemplate.getSpawnCell());
            room.setTemplateId(roomTemplate.getId());
            room.setWorldInstanceId(instance.getId());
            room.setMonsterSpawns(roomTemplate.getMonsterSpawns());
            roomInstances.put(roomTemplate.getId(), room);
        }

        for (RoomTemplate roomTemplate : template.getRoomTemplates().values()) {
            RoomInstance source = roomInstances.get(roomTemplate.getId());
            List<RoomPortal> portals = roomTemplate.getPortals().stream()
                    .map(portal -> toRoomPortal(roomInstances, source, portal)).toList();
            source.setPortals(portals);
        }

        long placementStart = System.currentTimeMillis();
        monsterService.placeMonsters(roomInstances.values());
        npcService.warmNpcs(List.of(template), roomInstances.values());
        itemService.warmRoomItems(roomInstances.values());
        long placementDurationMs = System.currentTimeMillis() - placementStart;

        instance.setRoomInstances(roomInstances);
        residentInstances.put(instance.getId(), instance);
        log.info("world_instance.materialized id={} worldTemplateId={} rooms={} placementDurationMs={}",
                instance.getId(), instance.getWorldTemplateId(), roomInstances.size(), placementDurationMs);
        return instance;
    }

    /**
     * Construit une {@link WorldInstance} neuve pour une party qui lance
     * {@code template} (voir {@code multi-world.md} Phase D), la matérialise tout
     * de suite — contrairement à {@link #getOrMaterialize}, {@code WorldEnter} a
     * besoin du graphe de {@link RoomInstance} immédiatement pour y faire spawn
     * chaque membre — puis publie l'événement de persistance, même ordre "mémoire
     * d'abord, événement ensuite" que le reste du domaine.
     */
    public WorldInstance createInstance(WorldTemplate template, Set<UUID> memberAccountIds, UUID leaderAccountId) {
        WorldInstance instance = new WorldInstance(UUID.randomUUID(), template.getId(), Instant.now(), leaderAccountId,
                memberAccountIds);
        materialize(instance);
        DomainEventPublisher.publish(new WorldInstanceCreated(instance));
        return instance;
    }

    @EventListener
    void onWorldInstanceCreated(WorldInstanceCreated event) {
        worldInstanceDao.insert(event.instance());
    }

    private RoomPortal toRoomPortal(Map<UUID, RoomInstance> roomInstances, RoomInstance source,
            RoomTemplatePortal portal) {
        RoomInstance target = roomInstances.get(portal.targetRoomTemplateId());
        return new RoomPortal(portal.cell(), portal.direction(), source, target, portal.targetCell());
    }

    /**
     * Résout {@code character.getCurrentRoomId()} (un id de {@code RoomTemplate},
     * voir {@code RoomService}) contre cette instance, avec repli sur la room de
     * départ pour un personnage tout juste créé (dont {@code currentRoomId} vaut
     * déjà l'id de la room de départ — ce repli couvre surtout le cas d'un id
     * orphelin, ex. contenu du monde modifié entre deux sessions).
     */
    public void spawnCharacterIntoInstance(GamePlayer character, WorldInstance instance) {
        RoomInstance room = instance.roomInstanceForTemplate(character.getCurrentRoomId())
                .or(instance::startingRoomInstance).orElseThrow(() -> new IllegalStateException(
                        "WorldInstance " + instance.getId() + " n'a aucune room de départ"));
        character.setWorldInstance(instance);
        character.spawnToRoom(room);
    }

    /**
     * Envoie {@code message} à tous les occupants connectés de {@code instance},
     * toutes {@link RoomInstance} confondues — délègue à
     * {@link RoomInstance#broadcast} room par room plutôt que de passer par
     * {@code GameWorld}, qui ne connaît que les connexions, pas la répartition par
     * room.
     */
    public void broadcastToInstance(WorldInstance instance, OutputMessage message, GamePlayer exclude) {
        for (RoomInstance room : instance.roomInstances()) {
            room.broadcast(message, exclude);
        }
    }
}
