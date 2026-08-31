package app.config;

import org.springframework.stereotype.Component;

import app.game.catalog.LevelCatalog;
import app.game.catalog.LevelCatalogHolder;

@Component
class LevelCatalogHolderInitializer {

    LevelCatalogHolderInitializer(LevelCatalog catalog) {
        LevelCatalogHolder.initialize(catalog);
    }
}
