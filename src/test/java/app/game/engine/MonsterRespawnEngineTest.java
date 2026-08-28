package app.game.engine;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import app.domain.MonsterSpawn;
import app.domain.MonsterSpawnGroup;
import app.domain.actor.Attribute;
import app.domain.actor.event.CharacterDied;
import app.domain.actor.instance.MonsterInstance;
import app.domain.map.Position;
import app.domain.world.CollisionGrid;
import app.domain.world.WorldInstance;
import app.domain.world.ZoneInstance;
import app.domain.world.ZoneTemplate;
import app.game.catalog.MonsterCatalog;

class MonsterRespawnEngineTest {

    private static final UUID TEMPLATE_ID = UUID.randomUUID();

    @Test
    void respawnsAfterDelayOnAFreeSpawnPoint() {
        MonsterSpawnGroup group = new MonsterSpawnGroup("goblins", 1, 0L);
        MonsterSpawn spawn1 = new MonsterSpawn(UUID.randomUUID(), TEMPLATE_ID, new Position(0, 0), "goblins");
        MonsterSpawn spawn2 = new MonsterSpawn(UUID.randomUUID(), TEMPLATE_ID, new Position(1, 0), "goblins");
        ZoneInstance zone = newZone(List.of(spawn1, spawn2), List.of(group));
        MonsterCatalog catalog = new StubMonsterCatalog();

        MonsterInstance monster = catalog.spawnMonster(spawn1, zone);
        zone.removeMonster(monster);

        MonsterRespawnEngine engine = new MonsterRespawnEngine(catalog);
        engine.onCharacterDied(new CharacterDied(monster, null));
        engine.tick();

        assertThat(zone.getMonsters()).hasSize(1);
    }

    @Test
    void doesNotRespawnBeforeDelayElapsed() {
        MonsterSpawnGroup group = new MonsterSpawnGroup("goblins", 1, 3600L);
        MonsterSpawn spawn1 = new MonsterSpawn(UUID.randomUUID(), TEMPLATE_ID, new Position(0, 0), "goblins");
        ZoneInstance zone = newZone(List.of(spawn1), List.of(group));
        MonsterCatalog catalog = new StubMonsterCatalog();

        MonsterInstance monster = catalog.spawnMonster(spawn1, zone);
        zone.removeMonster(monster);

        MonsterRespawnEngine engine = new MonsterRespawnEngine(catalog);
        engine.onCharacterDied(new CharacterDied(monster, null));
        engine.tick();

        assertThat(zone.getMonsters()).isEmpty();
    }

    @Test
    void neverExceedsMaxMonstersOfTheGroup() {
        MonsterSpawnGroup group = new MonsterSpawnGroup("goblins", 1, 0L);
        MonsterSpawn spawn1 = new MonsterSpawn(UUID.randomUUID(), TEMPLATE_ID, new Position(0, 0), "goblins");
        MonsterSpawn spawn2 = new MonsterSpawn(UUID.randomUUID(), TEMPLATE_ID, new Position(1, 0), "goblins");
        ZoneInstance zone = newZone(List.of(spawn1, spawn2), List.of(group));
        MonsterCatalog catalog = new StubMonsterCatalog();

        MonsterInstance survivor = catalog.spawnMonster(spawn1, zone);
        MonsterInstance dying = catalog.spawnMonster(spawn2, zone);
        zone.removeMonster(dying);

        MonsterRespawnEngine engine = new MonsterRespawnEngine(catalog);
        engine.onCharacterDied(new CharacterDied(dying, null));
        engine.tick();

        assertThat(zone.getMonsters()).containsExactly(survivor);
    }

    private static ZoneInstance newZone(List<MonsterSpawn> spawns, List<MonsterSpawnGroup> groups) {
        CollisionGrid terrain = new CollisionGrid(1, 1, 1.0, new BitSet());
        ZoneTemplate template = new ZoneTemplate(UUID.randomUUID(), "Test zone", "description", false, terrain,
                new Position(0, 0), spawns, groups, List.of());
        WorldInstance worldInstance = new WorldInstance(UUID.randomUUID(), UUID.randomUUID(), Instant.now());
        return new ZoneInstance(UUID.randomUUID(), template, worldInstance);
    }

    private static class StubMonsterCatalog extends MonsterCatalog {

        StubMonsterCatalog() {
            super(null, null);
        }

        @Override
        public MonsterInstance spawnMonster(MonsterSpawn spawn, ZoneInstance zone) {
            MonsterInstance monster = new MonsterInstance(spawn.id(), "Test Monster", spawn.templateId(), zone.getId(),
                    Map.of(Attribute.STRENGTH, 10), 10, spawn.position());
            monster.setCurrentZone(zone);
            zone.placeMonster(monster, spawn.position());
            return monster;
        }
    }
}
