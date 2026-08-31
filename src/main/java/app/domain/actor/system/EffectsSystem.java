package app.domain.actor.system;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import app.domain.ActiveEffect;
import app.domain.EffectCategory;
import app.domain.actor.ModifiedStat;

public final class EffectsSystem {

    public static final int MAX_BUFF_SLOTS = 6;
    public static final int MAX_DEBUFF_SLOTS = 4;

    private final Map<UUID, ActiveEffect> activeEffects = new ConcurrentHashMap<>();

    // Retourne l'effet évicté (le plus proche d'expirer, dans la même catégorie)
    // si l'application de ce nouvel effet a saturé les slots — vide sinon.
    // Renouveler un sort déjà actif (même skillId) n'évince jamais rien : ce
    // n'est pas un nouveau slot.
    public Optional<ActiveEffect> apply(ActiveEffect effect) {
        Optional<ActiveEffect> evicted = Optional.empty();
        if (!activeEffects.containsKey(effect.skillId())) {
            evicted = evictIfFull(effect.category());
        }
        activeEffects.put(effect.skillId(), effect);
        return evicted;
    }

    private Optional<ActiveEffect> evictIfFull(EffectCategory category) {
        int max = category == EffectCategory.BUFF ? MAX_BUFF_SLOTS : MAX_DEBUFF_SLOTS;
        List<ActiveEffect> sameCategory = active().stream().filter(effect -> effect.category() == category).toList();
        if (sameCategory.size() < max) {
            return Optional.empty();
        }
        ActiveEffect soonestToExpire = sameCategory.stream().min(Comparator.comparing(ActiveEffect::expiresAt))
                .orElseThrow();
        activeEffects.remove(soonestToExpire.skillId(), soonestToExpire);
        return Optional.of(soonestToExpire);
    }

    public int totalModifier(ModifiedStat stat) {
        Instant now = Instant.now();
        return activeEffects.values().stream().filter(effect -> effect.stat() == stat && now.isBefore(effect.expiresAt()))
                .mapToInt(ActiveEffect::amount).sum();
    }

    public List<ActiveEffect> active() {
        Instant now = Instant.now();
        return activeEffects.values().stream().filter(effect -> now.isBefore(effect.expiresAt())).toList();
    }

    public List<ActiveEffect> expireDue(Instant now) {
        List<ActiveEffect> expired = new ArrayList<>();
        for (ActiveEffect effect : activeEffects.values()) {
            if (!now.isBefore(effect.expiresAt())) {
                activeEffects.remove(effect.skillId(), effect);
                expired.add(effect);
            }
        }
        return expired;
    }

    public boolean isEmpty() {
        return activeEffects.isEmpty();
    }

    public void remove(UUID effectId) {
        activeEffects.remove(effectId);
    }
}
