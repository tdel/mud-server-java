package app.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

// periodicDamage : null sauf pour un effet à dégâts périodiques (poison,
// saignement). breaksOnAction : l'effet cesse dès que le porteur bouge, attaque
// ou incante (Relax, Fake Death, cf. ActiveEffectEngine). declaredCategory :
// catégorie imposée par le sort (Majesty est un buff malgré son malus
// d'esquive), null = déduite des modificateurs. status : altération d'état
// (STUN, ROOT...), null sinon. toggleMpPerTick > 0 : effet de toggle (Vicious
// Stance) sans échéance, qui consomme ces PM toutes les 5 s. cubic : cubique
// porté (Storm/Binding Cubic).
public record ActiveEffect(UUID skillId, String skillName, List<StatModifier> modifiers, Instant expiresAt,
        PeriodicDamage periodicDamage, boolean breaksOnAction, EffectCategory declaredCategory, AbnormalStatus status,
        PeriodicHeal periodicHeal, int toggleMpPerTick, CubicType cubic) {

    // Échéance d'un effet sans fin (pénalité de grade, cf. item.GradePenalty ;
    // toggles) : il dure tant que sa cause persiste, jamais expiré par
    // ActiveEffectEngine.
    public static final Instant PERMANENT = Instant.MAX;

    public ActiveEffect(UUID skillId, String skillName, List<StatModifier> modifiers, Instant expiresAt,
            PeriodicDamage periodicDamage, boolean breaksOnAction) {
        this(skillId, skillName, modifiers, expiresAt, periodicDamage, breaksOnAction, null, null, null, 0, null);
    }

    public ActiveEffect(UUID skillId, String skillName, List<StatModifier> modifiers, Instant expiresAt) {
        this(skillId, skillName, modifiers, expiresAt, null, false);
    }

    public boolean isPermanent() {
        return PERMANENT.equals(expiresAt);
    }

    public boolean isToggle() {
        return toggleMpPerTick > 0;
    }

    public EffectCategory category() {
        if (declaredCategory != null) {
            return declaredCategory;
        }
        return periodicDamage != null ? EffectCategory.DEBUFF : categoryOf(modifiers);
    }

    // Un seul modificateur négatif suffit à faire d'un effet un debuff.
    public static EffectCategory categoryOf(List<StatModifier> modifiers) {
        return modifiers.stream().anyMatch(modifier -> modifier.value() < 0)
                ? EffectCategory.DEBUFF
                : EffectCategory.BUFF;
    }
}
