package app.game.engine;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import app.domain.actor.event.SpellCast;
import app.domain.actor.instance.CharacterInstance;

@Component
public class RegenManaEngine {

    private static final Logger log = LoggerFactory.getLogger(RegenManaEngine.class);

    private static final long REGEN_INTERVAL_MS = 10_000L;
    private static final long TICK_INTERVAL_MS = 1_000L;

    private final Map<UUID, RegenState> regenerating = new ConcurrentHashMap<>();

    @EventListener
    void onSpellCast(SpellCast event) {
        register(event.caster());
    }

    public void register(CharacterInstance character) {
        if (isFull(character)) {
            return;
        }
        regenerating.putIfAbsent(character.getId(), new RegenState(character, System.currentTimeMillis()));
        log.debug("regen.mana.registered character={}", character.getId());
    }

    @Scheduled(fixedRate = TICK_INTERVAL_MS)
    void tick() {
        long now = System.currentTimeMillis();
        for (RegenState state : regenerating.values()) {
            if (now - state.lastRegenAt() < REGEN_INTERVAL_MS) {
                continue;
            }
            CharacterInstance character = state.character();
            character.regenerate(0, character.manaRegenAmountPerTick());

            if (isFull(character)) {
                regenerating.remove(character.getId());
                log.debug("regen.mana.completed character={}", character.getId());
            } else {
                regenerating.put(character.getId(), new RegenState(character, now));
            }
        }
    }

    private boolean isFull(CharacterInstance character) {
        return character.getCurrentMana() >= character.getMaxMana();
    }

    private record RegenState(CharacterInstance character, long lastRegenAt) {
    }
}
