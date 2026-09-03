package app.game.engine;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import app.domain.MonsterSpawn;
import app.domain.MonsterSpawnGroup;
import app.domain.actor.event.CharacterDied;
import app.domain.actor.instance.MonsterInstance;
import app.domain.world.MapInstance;
import app.game.catalog.MonsterCatalog;

@Component
public class MonsterRespawnEngine {

    private static final Logger log = LoggerFactory.getLogger(MonsterRespawnEngine.class);

    private static final long TICK_INTERVAL_MS = 1_000L;

    private final MonsterCatalog monsterCatalog;
    private final ConcurrentLinkedQueue<PendingRespawn> pending = new ConcurrentLinkedQueue<>();

    public MonsterRespawnEngine(MonsterCatalog monsterCatalog) {
        this.monsterCatalog = monsterCatalog;
    }

    @EventListener
    void onCharacterDied(CharacterDied event) {
        if (!(event.character() instanceof MonsterInstance monster)) {
            return;
        }
        MapInstance map = monster.getMotionSystem().getCurrentMap();
        Optional<MonsterSpawn> spawn = map.getMonsterSpawns().stream()
                .filter(candidate -> candidate.id().equals(monster.getId())).findFirst();
        if (spawn.isEmpty()) {
            return;
        }
        MonsterSpawnGroup group = map.getMonsterSpawnGroups().stream()
                .filter(candidate -> candidate.id().equals(spawn.get().groupId())).findFirst()
                .orElseThrow(() -> new IllegalStateException("Spawn " + spawn.get().id() + " référence le groupe "
                        + spawn.get().groupId() + ", absent de la map " + map.getId()));
        pending.add(new PendingRespawn(map, group, System.currentTimeMillis()));
        log.info("monster.respawn_scheduled map={} group={} delaySeconds={}", map.getId(), group.id(),
                group.respawnDelaySeconds());
    }

    @Scheduled(fixedRate = TICK_INTERVAL_MS)
    void tick() {
        long now = System.currentTimeMillis();
        List<PendingRespawn> due = pending.stream()
                .filter(entry -> now - entry.diedAt() >= entry.group().respawnDelaySeconds() * 1000).toList();

        for (PendingRespawn entry : due) {
            MapInstance map = entry.map();
            MonsterSpawnGroup group = entry.group();
            List<MonsterSpawn> groupSpawns = map.getMonsterSpawns().stream()
                    .filter(spawn -> spawn.groupId().equals(group.id())).toList();
            Set<UUID> occupiedIds = map.getMonsters().stream().map(MonsterInstance::getId).collect(Collectors.toSet());
            long occupiedCount = groupSpawns.stream().filter(spawn -> occupiedIds.contains(spawn.id())).count();
            if (occupiedCount >= group.maxMonsters()) {
                continue;
            }
            Optional<MonsterSpawn> freeSpawn = groupSpawns.stream().filter(spawn -> !occupiedIds.contains(spawn.id()))
                    .findFirst();
            if (freeSpawn.isEmpty()) {
                continue;
            }
            MonsterInstance monster = monsterCatalog.spawnMonster(freeSpawn.get(), map);
            pending.remove(entry);
            monster.getKnownList().populate();
            log.info("monster.respawned map={} group={} monsterId={}", map.getId(), group.id(), monster.getId());
        }
    }

    private record PendingRespawn(MapInstance map, MonsterSpawnGroup group, long diedAt) {
    }
}
