package fr.idev.mudserver.game.engine;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import fr.idev.mudserver.domain.actor.event.CharacterDied;
import fr.idev.mudserver.domain.actor.event.GamePlayerDied;
import fr.idev.mudserver.domain.world.ZoneInstance;
import fr.idev.mudserver.network.message.ingame.GamePlayerDefeated;
import fr.idev.mudserver.network.message.ingame.MonsterDefeated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import fr.idev.mudserver.domain.actor.event.GamePlayerDamaged;
import fr.idev.mudserver.domain.actor.instance.CharacterInstance;
import org.springframework.transaction.annotation.Transactional;

@Component
public class RegenHealthEngine {

    private static final Logger log = LoggerFactory.getLogger(RegenHealthEngine.class);

    private static final long REGEN_INTERVAL_MS = 10_000L;
    private static final long TICK_INTERVAL_MS = 1_000L;

    private final Map<UUID, RegenState> regenerating = new ConcurrentHashMap<>();

    @EventListener
    void onGamePlayerDamaged(GamePlayerDamaged event) {
        register(event.character());
    }

    public void register(CharacterInstance character) {
        if (isFull(character)) {
            return;
        }
        regenerating.putIfAbsent(character.getId(), new RegenState(character, System.currentTimeMillis()));
    }

    @Scheduled(fixedRate = TICK_INTERVAL_MS)
    void tick() {
        long now = System.currentTimeMillis();
        for (RegenState state : regenerating.values()) {
            if (now - state.lastRegenAt() < REGEN_INTERVAL_MS) {
                continue;
            }
            CharacterInstance character = state.character();
            character.regenerate(character.healthRegenAmountPerTick(), 0);

            if (isFull(character)) {
                regenerating.remove(character.getId());
            } else {
                regenerating.put(character.getId(), new RegenState(character, now));
            }
        }
    }

    private boolean isFull(CharacterInstance character) {
        return character.getCurrentHealth() >= character.getMaxHealth();
    }

    @EventListener
    @Transactional
    void onCharacterDied(CharacterDied event) {
        ZoneInstance zone = event.character().getCurrentZone();
        zone.removeMonster(event.character());
        zone.broadcast(new MonsterDefeated(event.character().getName()), null);
        log.info("regenhp.monster_removed_from_zone monster={} zone={}", event.character().getName(), zone.getName());

        event.character().getTemplate().grantLootTo(event.killer());
    }

    @EventListener
    void onGamePlayerDied(GamePlayerDied event) {
        ZoneInstance zone = event.character().getCurrentZone();
        zone.broadcast(new GamePlayerDefeated(event.character().getName(), event.killer().getName()),
                event.character());
        log.info("regenhp.player_defeated character={} killer={} zone={}", event.character().getName(),
                event.killer().getName(), zone.getName());
    }

    private record RegenState(CharacterInstance character, long lastRegenAt) {
    }
}
