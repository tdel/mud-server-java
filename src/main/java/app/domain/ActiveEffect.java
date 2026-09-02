package app.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ActiveEffect(UUID skillId, String skillName, List<StatModifier> modifiers, Instant expiresAt) {

    public EffectCategory category() {
        return modifiers.stream().anyMatch(modifier -> modifier.value() < 0)
                ? EffectCategory.DEBUFF
                : EffectCategory.BUFF;
    }
}
