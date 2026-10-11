package app.game;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import app.game.engine.MovementEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import app.domain.Account;
import app.domain.world.MapInstance;
import app.domain.world.MapTemplate;
import app.domain.world.WorldInstance;
import app.domain.world.WorldTemplate;
import app.domain.world.WorldTemplateSummary;
import app.domain.actor.AbstractNpc;
import app.domain.actor.instance.PlayerInstance;
import app.domain.actor.instance.NpcSellerInstance;
import app.domain.actor.template.NpcTemplate;
import app.domain.map.Position;
import app.game.catalog.MonsterCatalog;
import app.game.catalog.WorldTemplateCatalog;
import app.network.Connection;
import app.network.ConnectionState;
import app.persistence.CharacterDao;

@Service
public class WorldInstanceService {

    private static final Logger log = LoggerFactory.getLogger(WorldInstanceService.class);

    private final WorldTemplateCatalog worldTemplateService;
    private final MonsterCatalog monsterService;
    private final MovementEngine movementEngine;
    private final CharacterDao characterDao;

    private WorldInstance defaultInstance;

    public WorldInstanceService(WorldTemplateCatalog worldTemplateService, MonsterCatalog monsterService,
            MovementEngine movementEngine, CharacterDao characterDao) {
        this.worldTemplateService = worldTemplateService;
        this.monsterService = monsterService;
        this.movementEngine = movementEngine;
        this.characterDao = characterDao;
    }

    public WorldInstance materializeDefaultWorld() {
        WorldTemplateSummary summary = worldTemplateService.theOnlyTemplate();
        WorldTemplate template = worldTemplateService.findById(summary.id())
                .orElseThrow(() -> new IllegalStateException("WorldTemplate " + summary.id() + " absent"));

        WorldInstance instance = new WorldInstance(WorldInstance.DEFAULT_ID, template.getId(), Instant.now());

        Map<UUID, MapInstance> mapInstances = new LinkedHashMap<>();
        for (MapTemplate mapTemplate : template.getMapTemplates().values()) {
            UUID mapInstanceId = MapInstance.deterministicId(instance.getId(), mapTemplate.getId());
            mapInstances.put(mapTemplate.getId(), new MapInstance(mapInstanceId, mapTemplate, instance));
        }

        long placementStart = System.currentTimeMillis();
        monsterService.placeMonsters(mapInstances.values());
        warmNpcs(List.of(template), mapInstances.values());
        long placementDurationMs = System.currentTimeMillis() - placementStart;

        instance.setMapInstances(mapInstances);
        this.defaultInstance = instance;
        log.info("world.materialized id={} worldTemplateId={} maps={} placementDurationMs={}", instance.getId(),
                instance.getWorldTemplateId(), mapInstances.size(), placementDurationMs);
        return instance;
    }

    public WorldInstance getDefaultInstance() {
        if (defaultInstance == null) {
            throw new IllegalStateException("Le monde par défaut n'est pas encore matérialisé");
        }
        return defaultInstance;
    }

    public boolean isDefaultWorldMaterialized() {
        return defaultInstance != null;
    }

    public List<PlayerInstance> findCharactersFor(Account account) {
        return characterDao.findAllByAccount(account, getDefaultInstance());
    }

    public Optional<PlayerInstance> findCharacterByName(Account account, String name) {
        return characterDao.findByAccountAndName(account, getDefaultInstance(), name);
    }

    public void exitGame(Connection connection) {
        if (connection.state() != ConnectionState.INGAME) {
            return;
        }

        PlayerInstance character = connection.character();
        MapInstance map = character.getMotionSystem().getCurrentMap();
        WorldInstance instance = character.getWorldInstance();

        // Chaque étape est isolée : un échec de sauvegarde (ou de diffusion) ne doit
        // jamais laisser le personnage en mémoire — fantôme visible sur la map, et
        // personnage impossible à recharger (WorldInstance.reservePlayer le refuse
        // tant qu'il est dans players).
        try {
            characterDao.update(character);
            Position position = character.getMotionSystem().getPosition();
            if (position != null) {
                characterDao.updatePosition(character.getId(), position.x(), position.y());
            }
        } catch (Exception e) {
            log.error("world.session_save_failed character={}", character.getName(), e);
        }
        try {
            movementEngine.stopMovement(character);
            if (map != null) {
                map.disconnect(character);
            }
        } catch (Exception e) {
            log.error("world.session_map_leave_failed character={}", character.getName(), e);
        } finally {
            try {
                instance.removePlayer(character);
            } finally {
                // Un moteur encore porteur d'une référence à ce personnage ne peut plus
                // écrire sur la socket (PlayerInstance.send() no-op sans connexion).
                character.setConnection(null);
            }
        }
        log.info("world.session_ended character={} map={}", character.getName(), map != null ? map.getName() : null);
        MDC.remove("character");
    }

    private void warmNpcs(Collection<WorldTemplate> worldTemplates, Collection<MapInstance> maps) {
        Map<UUID, MapInstance> mapsByTemplateId = new ConcurrentHashMap<>();
        for (MapInstance map : maps) {
            mapsByTemplateId.put(map.getTemplateId(), map);
        }

        int count = 0;
        for (WorldTemplate worldTemplate : worldTemplates) {
            for (NpcTemplate npcTemplate : worldTemplate.getNpcTemplates().values()) {
                MapInstance map = mapsByTemplateId.get(npcTemplate.mapTemplateId());
                if (map == null) {
                    throw new IllegalStateException("NPC " + npcTemplate.id() + " référence la map "
                            + npcTemplate.mapTemplateId() + ", absente du monde " + worldTemplate.getShortName());
                }
                placeNpc(npcTemplate, map);
                count++;
            }
        }

        log.info("npc.instances_placed count={}", count);
    }

    private void placeNpc(NpcTemplate template, MapInstance map) {
        AbstractNpc npc = template.shop() != null
                ? new NpcSellerInstance(template.id(), template, map)
                : new AbstractNpc(template.id(), template, map);
        map.placeNpc(npc, template.position());
    }
}
