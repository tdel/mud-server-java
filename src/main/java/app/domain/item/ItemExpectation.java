package app.domain.item;

import java.util.List;
import java.util.UUID;

// Prérequis d'un EquipmentItem : compétences (+ level minimum) à posséder pour
// le porter sans malus. Seul cas d'usage actuel : le grade d'un objet face à
// l'expertise du personnage — non remplis, l'objet déclenche une pénalité de
// grade (GradePenalty, cf. InventorySystem.refreshGradePenalties).
public record ItemExpectation(List<SkillRequirement> conditions) {

    public record SkillRequirement(UUID skillId, int level) {
    }
}
