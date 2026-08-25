package app.config;

import org.springframework.stereotype.Component;

import app.game.catalog.SpellCatalog;
import app.game.catalog.SpellCatalogHolder;

@Component
class SpellCatalogHolderInitializer {

    SpellCatalogHolderInitializer(SpellCatalog catalog) {
        SpellCatalogHolder.initialize(catalog);
    }
}
