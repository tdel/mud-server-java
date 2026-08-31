package app.domain.actor;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import app.domain.Account;
import app.domain.actor.event.DomainEventPublisher;
import app.domain.actor.instance.CharacterInstance;
import app.domain.item.ArmorCategory;
import app.domain.item.EquipmentSlot;
import app.domain.item.Item;
import app.domain.item.ItemGrade;
import app.domain.item.ItemSet;
import app.domain.item.ItemTemplate;
import app.domain.item.ItemType;
import app.domain.map.Position;
import app.domain.world.CollisionGrid;
import app.domain.world.MapInstance;
import app.domain.world.MapTemplate;
import app.domain.world.WorldInstance;
import app.game.catalog.ItemSetCatalog;
import app.game.catalog.ItemSetCatalogHolder;
import tools.jackson.databind.ObjectMapper;

class CharacterInstanceSetBonusTest {

    // Set réel de data/item_sets.json : palier 2 pièces = +5 P.Def, palier 3
    // pièces = +12 P.Def / -3 Evasion (cumulatif avec le palier 2).
    private static final String SET_ID = "plate-set";

    @BeforeAll
    static void initStatics() {
        DomainEventPublisher.initialize(event -> {
        });
        ItemSetCatalog catalog = new ItemSetCatalog(new ObjectMapper());
        catalog.warmItemSets();
        ItemSetCatalogHolder.initialize(catalog);
    }

    @Test
    void twoEquippedSetPiecesApplyTheTwoPieceBonusOnly() {
        CharacterInstance character = createCharacter();
        equip(character, setTemplate(), EquipmentSlot.CHEST);
        equip(character, setTemplate(), EquipmentSlot.HEAD);

        int baseline = character.getPDef();
        assertThat(character.getEffectivePDef()).isEqualTo(baseline + 5);
        assertThat(character.getEffectiveEvasion()).isEqualTo(character.getEvasion());
    }

    @Test
    void threeEquippedSetPiecesStackBothTierBonuses() {
        CharacterInstance character = createCharacter();
        equip(character, setTemplate(), EquipmentSlot.CHEST);
        equip(character, setTemplate(), EquipmentSlot.HEAD);
        equip(character, setTemplate(), EquipmentSlot.OFF_HAND);

        int baselinePDef = character.getPDef();
        int baselineEvasion = character.getEvasion();
        assertThat(character.getEffectivePDef()).isEqualTo(baselinePDef + 5 + 12);
        assertThat(character.getEffectiveEvasion()).isEqualTo(baselineEvasion - 3);
    }

    @Test
    void onePieceOnlyGrantsNoSetBonus() {
        CharacterInstance character = createCharacter();
        equip(character, setTemplate(), EquipmentSlot.CHEST);

        assertThat(character.getEffectivePDef()).isEqualTo(character.getPDef());
    }

    private void equip(CharacterInstance character, ItemTemplate template, EquipmentSlot slot) {
        character.getInventory().addItem(new Item(UUID.randomUUID(), template, character, slot));
    }

    private ItemTemplate setTemplate() {
        return new ItemTemplate(UUID.randomUUID(), "set piece", "description", ItemType.ARMOR, 1, ArmorCategory.LIGHT,
                0, 0, 0, 0, 0, 0, 0, 10, List.of(), Map.of(), ItemGrade.D, SET_ID);
    }

    private CharacterInstance createCharacter() {
        WorldInstance world = worldWithStartingMap();
        Account account = new Account(UUID.randomUUID(), "login", "hash");
        return world.createCharacter(account, "hero", Gender.MAN, Race.HUMAN, CharacterClass.FIGHTER);
    }

    private WorldInstance worldWithStartingMap() {
        CollisionGrid grid = new CollisionGrid(5, 5, 1.0, allWalkable(5, 5));
        MapTemplate template = new MapTemplate(UUID.randomUUID(), "map", "description", true, grid, new Position(1, 1),
                List.of(), List.of(), List.of());
        WorldInstance world = new WorldInstance(UUID.randomUUID(), UUID.randomUUID(), Instant.now());
        MapInstance mapInstance = new MapInstance(UUID.randomUUID(), template, world);
        world.setMapInstances(Map.of(template.getId(), mapInstance));
        return world;
    }

    private static BitSet allWalkable(int width, int height) {
        BitSet walkable = new BitSet(width * height);
        walkable.set(0, width * height);
        return walkable;
    }
}
