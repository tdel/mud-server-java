package app.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

// periodicDamage : null sauf pour un effet à dégâts périodiques (poison).
// breaksOnAction : l'effet cesse dès que le porteur bouge, attaque ou incante
// (Relax, cf. ActiveEffectEngine).
public record ActiveEffect(UUID skillId, String skillName, List<StatModifier> modifiers, Instant expiresAt,
        PeriodicDamage periodicDamage, boolean breaksOnAction) {

    public ActiveEffect(UUID skillId, String skillName, List<StatModifier> modifiers, Instant expiresAt) {
        this(skillId, skillName, modifiers, expiresAt, null, false);
    }

    public EffectCategory category() {
        return periodicDamage != null ? EffectCategory.DEBUFF : categoryOf(modifiers);
    }

    // Un seul modificateur négatif suffit à faire d'un effet un debuff.
    public static EffectCategory categoryOf(List<StatModifier> modifiers) {
        return modifiers.stream().anyMatch(modifier -> modifier.value() < 0)
                ? EffectCategory.DEBUFF
                : EffectCategory.BUFF;
    }
}
