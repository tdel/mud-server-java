package app.game.catalog;

import java.util.UUID;

import app.domain.PassiveSkill;

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
}
