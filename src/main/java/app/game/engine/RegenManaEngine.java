package app.game.engine;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import app.domain.actor.event.PlayerLoadedInWorld;
import app.domain.actor.event.SpellCast;
import app.domain.actor.instance.CharacterInstance;

@Component
public class RegenManaEngine {

    private static final Logger log = LoggerFactory.getLogger(RegenManaEngine.class);

    private static final long TICK_INTERVAL_MS = 1_000L;

    private final Map<UUID, CharacterInstance> regenerating = new ConcurrentHashMap<>();

    @EventListener
    void onSpellCast(SpellCast event) {
        if (event.caster() instanceof CharacterInstance character) {
            register(character);
        }
    }

    @EventListener
    void onPlayerLoadedInWorld(PlayerLoadedInWorld event) {
        register(event.character());
    }

    public void register(CharacterInstance character) {
        if (isFull(character)) {
            return;
        }
        regenerating.putIfAbsent(character.getId(), character);
        log.debug("regen.mana.registered character={}", character.getId());
    }

    @Scheduled(fixedRate = TICK_INTERVAL_MS)
    void tick() {
        for (CharacterInstance character : regenerating.values()) {
            character.regenerate(0, character.manaRegenAmountPerTick());

            if (isFull(character)) {
                regenerating.remove(character.getId());
                log.debug("regen.mana.completed character={}", character.getId());
            }
        }
    }

    private boolean isFull(CharacterInstance character) {
        return character.getCurrentMana() >= character.getMaxMana();
    }
}
