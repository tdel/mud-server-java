package fr.idev.mudserver.game;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import fr.idev.mudserver.domain.actor.instance.CharacterInstance;

@Component
public class RegenHealthEngine {

    private static final long REGEN_INTERVAL_MS = 10_000L;
    private static final long TICK_INTERVAL_MS = 1_000L;

    private final Map<UUID, RegenState> regenerating = new ConcurrentHashMap<>();

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

    private record RegenState(CharacterInstance character, long lastRegenAt) {
    }
}
