package app.domain;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import app.domain.item.ArmorCategory;
import app.domain.item.ItemGrade;

// Deux familles de compétences passives partagent ce record : les passifs de
// grade (Expertise Grade : chaque level débloque un grade d'équipement) et les
// passifs de stats (Weapon Mastery, Armor Mastery, Anti Magic : modificateurs
// permanents, cf. SkillSystem.passiveAdditive/passiveFactor).
public record PassiveSkill(UUID id, String name, String description, List<PassiveLevel> levels) {

    public int maxLevel() {
        return levels.size();
    }

    public ItemGrade gradeAt(int level) {
        ItemGrade grade = levels.get(level - 1).grade();
        return grade == null ? ItemGrade.NOGRADE : grade;
    }

    public List<PassiveModifier> modifiersAt(int level) {
        return levels.get(level - 1).modifiers();
    }

    public record PassiveLevel(int level, ItemGrade grade, List<PassiveModifier> modifiers) {
    }

    // armor : types d'armure (plastron porté) exigés pour que le modificateur
    // s'applique (Mana Recovery : robe ; Armor Mastery : esquive en armure
    // légère) ; notArmor : types qui l'annulent, torse nu compris dans le reste
    // (Spellcraft, Magician's Movement : pénalité hors robe, comme L2J). Vides =
    // aucune condition.
    public record PassiveModifier(StatModifier modifier, Set<ArmorCategory> armor, Set<ArmorCategory> notArmor) {

        public boolean conditional() {
            return !armor.isEmpty() || !notArmor.isEmpty();
        }

        public boolean appliesTo(ArmorCategory chestArmor) {
            boolean required = armor.isEmpty() || chestArmor != null && armor.contains(chestArmor);
            boolean excluded = chestArmor != null && notArmor.contains(chestArmor);
            return required && !excluded;
        }
    }
}
