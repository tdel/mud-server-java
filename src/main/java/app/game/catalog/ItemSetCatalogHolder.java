package app.game.catalog;

import java.util.Optional;

import app.domain.item.ItemSet;

public final class ItemSetCatalogHolder {

    private static volatile ItemSetCatalog catalog;

    private ItemSetCatalogHolder() {
    }

    public static void initialize(ItemSetCatalog catalog) {
        ItemSetCatalogHolder.catalog = catalog;
    }

    public static ItemSet getById(String setId) {
        return catalog.getById(setId);
    }

    public static Optional<ItemSet> findById(String setId) {
        return catalog.findById(setId);
    }
}
