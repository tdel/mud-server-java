package app.config;

import org.springframework.stereotype.Component;

import app.game.catalog.ItemSetCatalog;
import app.game.catalog.ItemSetCatalogHolder;

@Component
class ItemSetCatalogHolderInitializer {

    ItemSetCatalogHolderInitializer(ItemSetCatalog catalog) {
        ItemSetCatalogHolder.initialize(catalog);
    }
}
