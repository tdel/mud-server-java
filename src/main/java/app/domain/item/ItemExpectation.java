package app.domain.item;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import app.domain.EffectCategory;
import app.domain.StatModifier;

// Prérequis d'un EquipmentItem : conditions (compétences + level minimum) à
// remplir pour le porter sans malus, actions (effets à appliquer) sinon. Seul
// cas d'usage actuel : le grade d'un objet face à l'expertise du personnage
// (cf. InventorySystem.recomputeGradePenalty), mais le schéma reste générique.
public record ItemExpectation(List<SkillRequirement> conditions, List<ExpectationEffect> actions) {

    public record SkillRequirement(UUID skillId, int level) {
    }

    public record ExpectationEffect(String name, Duration duration, EffectCategory type, List<StatModifier> modifiers) {
    }
}
