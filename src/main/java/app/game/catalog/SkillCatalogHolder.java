package app.game.catalog;

import java.util.List;

import app.domain.ActiveSkill;
import app.domain.actor.CharacterClass;

public final class SkillCatalogHolder {

    private static volatile SkillCatalog catalog;

    private SkillCatalogHolder() {
    }

    public static void initialize(SkillCatalog catalog) {
        SkillCatalogHolder.catalog = catalog;
    }

    public static List<ActiveSkill> skillsLearnableAt(CharacterClass characterClass, int level) {
        return catalog.skillsLearnableAt(characterClass, level);
    }
}
