package app.game;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import app.game.engine.MovementEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import app.domain.Account;
import app.domain.world.ZoneInstance;
import app.domain.world.ZoneTemplate;
import app.domain.world.WorldInstance;
import app.domain.world.WorldTemplate;
import app.domain.world.WorldTemplateSummary;
import app.domain.actor.instance.CharacterInstance;
import app.game.catalog.MonsterCatalog;
import app.game.catalog.NpcCatalog;
import app.game.catalog.WorldTemplateCatalog;
import app.network.Connection;
import app.network.ConnectionState;
import app.persistence.CharacterDao;

@Service
public class WorldInstanceService {

    private static final Logger log = LoggerFactory.getLogger(WorldInstanceService.class);

    private final WorldTemplateCatalog worldTemplateService;
    private final MonsterCatalog monsterService;
    private final NpcCatalog npcService;
    private final MovementEngine movementEngine;
    private final CharacterDao characterDao;

    private WorldInstance defaultInstance;

    public WorldInstanceService(WorldTemplateCatalog worldTemplateService, MonsterCatalog monsterService,
            NpcCatalog npcService, MovementEngine movementEngine, CharacterDao characterDao) {
        this.worldTemplateService = worldTemplateService;
        this.monsterService = monsterService;
        this.npcService = npcService;
        this.movementEngine = movementEngine;
        this.characterDao = characterDao;
    }

    public WorldInstance materializeDefaultWorld() {
        WorldTemplateSummary summary = worldTemplateService.theOnlyTemplate();
        WorldTemplate template = worldTemplateService.findById(summary.id())
                .orElseThrow(() -> new IllegalStateException("WorldTemplate " + summary.id() + " absent"));

        WorldInstance instance = new WorldInstance(WorldInstance.DEFAULT_ID, template.getId(), Instant.now());

        Map<UUID, ZoneInstance> zoneInstances = new LinkedHashMap<>();
        for (ZoneTemplate zoneTemplate : template.getZoneTemplates().values()) {
            UUID zoneInstanceId = ZoneInstance.deterministicId(instance.getId(), zoneTemplate.getId());
            zoneInstances.put(zoneTemplate.getId(), new ZoneInstance(zoneInstanceId, zoneTemplate, instance));
        }

        long placementStart = System.currentTimeMillis();
        monsterService.placeMonsters(zoneInstances.values());
        npcService.warmNpcs(List.of(template), zoneInstances.values());
        long placementDurationMs = System.currentTimeMillis() - placementStart;

        instance.setZoneInstances(zoneInstances);
        this.defaultInstance = instance;
        log.info("world.materialized id={} worldTemplateId={} zones={} placementDurationMs={}", instance.getId(),
                instance.getWorldTemplateId(), zoneInstances.size(), placementDurationMs);
        return instance;
    }

    public WorldInstance getDefaultInstance() {
        if (defaultInstance == null) {
            throw new IllegalStateException("Le monde par défaut n'est pas encore matérialisé");
        }
        return defaultInstance;
    }

    public List<CharacterInstance> findCharactersFor(Account account) {
        return characterDao.findAllByAccount(account, getDefaultInstance());
    }

    public Optional<CharacterInstance> findCharacterByName(Account account, String name) {
        return characterDao.findByAccountAndName(account, getDefaultInstance(), name);
    }

    public void exitGame(Connection connection) {
        if (connection.state() != ConnectionState.INGAME) {
            return;
        }

        CharacterInstance character = connection.character();
        ZoneInstance zone = character.getCurrentZone();
        WorldInstance instance = character.getWorldInstance();

        characterDao.update(character);
        movementEngine.stopMovement(character);
        zone.disconnect(character);
        instance.removePlayer(character);
        log.info("world.session_ended character={} zone={}", character.getName(), zone.getName());
        MDC.remove("character");
    }

}
