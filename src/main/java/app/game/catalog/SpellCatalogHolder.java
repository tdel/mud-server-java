package app.game.catalog;

import java.util.List;

import app.domain.Spell;
import app.domain.actor.CharacterClass;

public final class SpellCatalogHolder {

    private static volatile SpellCatalog catalog;

    private SpellCatalogHolder() {
    }

    public static void initialize(SpellCatalog catalog) {
        SpellCatalogHolder.catalog = catalog;
    }

    public static List<Spell> spellsLearnableAt(CharacterClass characterClass, int level) {
        return catalog.spellsLearnableAt(characterClass, level);
    }
}
