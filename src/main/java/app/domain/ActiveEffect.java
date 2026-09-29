package app.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ActiveEffect(UUID skillId, String skillName, List<StatModifier> modifiers, Instant expiresAt) {

    public EffectCategory category() {
        return categoryOf(modifiers);
    }

    // Un seul modificateur négatif suffit à faire d'un effet un debuff.
    public static EffectCategory categoryOf(List<StatModifier> modifiers) {
        return modifiers.stream().anyMatch(modifier -> modifier.value() < 0)
                ? EffectCategory.DEBUFF
                : EffectCategory.BUFF;
    }
}
