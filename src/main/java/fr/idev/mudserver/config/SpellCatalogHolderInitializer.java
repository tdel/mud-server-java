package fr.idev.mudserver.config;

import org.springframework.stereotype.Component;

import fr.idev.mudserver.game.catalog.SpellCatalog;
import fr.idev.mudserver.game.catalog.SpellCatalogHolder;

@Component
class SpellCatalogHolderInitializer {

    SpellCatalogHolderInitializer(SpellCatalog catalog) {
        SpellCatalogHolder.initialize(catalog);
    }
}
