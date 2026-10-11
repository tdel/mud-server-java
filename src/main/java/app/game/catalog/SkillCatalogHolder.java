package app.game.catalog;

import java.util.List;

import app.domain.actor.SkillTree;

public final class SkillCatalogHolder {

    private static volatile SkillCatalog catalog;

    private SkillCatalogHolder() {
    }

    public static void initialize(SkillCatalog catalog) {
        SkillCatalogHolder.catalog = catalog;
    }

    public static List<SkillCatalog.LearnableSkill> skillsLearnableAt(SkillTree tree, int level) {
        return catalog.skillsLearnableAt(tree, level);
    }
}
