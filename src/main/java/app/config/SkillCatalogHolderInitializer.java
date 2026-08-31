package app.config;

import org.springframework.stereotype.Component;

import app.game.catalog.SkillCatalog;
import app.game.catalog.SkillCatalogHolder;

@Component
class SkillCatalogHolderInitializer {

    SkillCatalogHolderInitializer(SkillCatalog catalog) {
        SkillCatalogHolder.initialize(catalog);
    }
}
