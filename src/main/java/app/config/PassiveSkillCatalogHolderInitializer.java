package app.config;

import org.springframework.stereotype.Component;

import app.game.catalog.PassiveSkillCatalog;
import app.game.catalog.PassiveSkillCatalogHolder;

@Component
class PassiveSkillCatalogHolderInitializer {

    PassiveSkillCatalogHolderInitializer(PassiveSkillCatalog catalog) {
        PassiveSkillCatalogHolder.initialize(catalog);
    }
}
