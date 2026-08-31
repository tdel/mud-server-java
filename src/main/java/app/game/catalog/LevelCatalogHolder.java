package app.game.catalog;

public final class LevelCatalogHolder {

    private static volatile LevelCatalog catalog;

    private LevelCatalogHolder() {
    }

    public static void initialize(LevelCatalog catalog) {
        LevelCatalogHolder.catalog = catalog;
    }

    public static int xpRequiredForLevel(int level) {
        return catalog.xpRequiredForLevel(level);
    }

    public static int maxLevel() {
        return catalog.maxLevel();
    }
}
