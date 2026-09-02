package app.game.catalog;

import java.util.List;
import java.util.UUID;

import app.domain.PassiveSkill;
import app.domain.actor.CharacterClass;

public final class PassiveSkillCatalogHolder {

    private static volatile PassiveSkillCatalog catalog;

    private PassiveSkillCatalogHolder() {
    }

    public static void initialize(PassiveSkillCatalog catalog) {
        PassiveSkillCatalogHolder.catalog = catalog;
    }

    public static PassiveSkill getById(UUID passiveSkillId) {
        return catalog.getById(passiveSkillId);
    }

    public static boolean isKnownId(UUID passiveSkillId) {
        return catalog.isKnownId(passiveSkillId);
    }

    public static List<PassiveSkillCatalog.LearnablePassiveSkill> passiveSkillsLearnableAt(
            CharacterClass characterClass, int level) {
        return catalog.passiveSkillsLearnableAt(characterClass, level);
    }
}
