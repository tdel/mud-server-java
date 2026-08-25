package app.domain.actor.component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import app.domain.actor.ModifiedStat;

public final class ActiveEffects {

    private final Map<UUID, ActiveEffect> effects = new ConcurrentHashMap<>();

    public void apply(ActiveEffect effect) {
        effects.put(effect.spellId(), effect);
    }

    public int totalModifier(ModifiedStat stat) {
        Instant now = Instant.now();
        return effects.values().stream().filter(effect -> effect.stat() == stat && now.isBefore(effect.expiresAt()))
                .mapToInt(ActiveEffect::amount).sum();
    }

    public List<ActiveEffect> active() {
        Instant now = Instant.now();
        return effects.values().stream().filter(effect -> now.isBefore(effect.expiresAt())).toList();
    }

    public List<ActiveEffect> expireDue(Instant now) {
        List<ActiveEffect> expired = new ArrayList<>();
        for (ActiveEffect effect : effects.values()) {
            if (!now.isBefore(effect.expiresAt())) {
                effects.remove(effect.spellId(), effect);
                expired.add(effect);
            }
        }
        return expired;
    }

    public boolean isEmpty() {
        return effects.isEmpty();
    }
}
