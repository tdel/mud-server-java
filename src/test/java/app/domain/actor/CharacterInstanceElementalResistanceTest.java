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
import app.domain.SpellElement;
import app.domain.actor.event.DomainEventPublisher;
import app.domain.actor.instance.CharacterInstance;
import app.domain.item.ArmorCategory;
import app.domain.item.EquipmentSlot;
import app.domain.item.Item;
import app.domain.item.ItemGrade;
import app.domain.item.ItemTemplate;
import app.domain.item.ItemType;
import app.domain.map.Position;
import app.domain.world.CollisionGrid;
import app.domain.world.MapInstance;
import app.domain.world.MapTemplate;
import app.domain.world.WorldInstance;

class CharacterInstanceElementalResistanceTest {

    @BeforeAll
    static void initDomainEventPublisher() {
        DomainEventPublisher.initialize(event -> {
        });
    }

    @Test
    void getElementalResistanceSumsOnlyEquippedItems() {
        CharacterInstance character = createCharacter();

        ItemTemplate chest = armorTemplate(Map.of(SpellElement.FIRE, 10, SpellElement.DARK, 5));
        ItemTemplate helmet = armorTemplate(Map.of(SpellElement.FIRE, 4));
        ItemTemplate carriedOnly = armorTemplate(Map.of(SpellElement.FIRE, 100));

        equip(character, chest, EquipmentSlot.CHEST);
        equip(character, helmet, EquipmentSlot.HEAD);
        character.getInventory().addItem(new Item(UUID.randomUUID(), carriedOnly, character, null));

        assertThat(character.getElementalResistance(SpellElement.FIRE)).isEqualTo(14);
        assertThat(character.getElementalResistance(SpellElement.DARK)).isEqualTo(5);
        assertThat(character.getElementalResistance(SpellElement.WATER)).isZero();
    }

    private void equip(CharacterInstance character, ItemTemplate template, EquipmentSlot slot) {
        Item item = new Item(UUID.randomUUID(), template, character, slot);
        character.getInventory().addItem(item);
    }

    private ItemTemplate armorTemplate(Map<SpellElement, Integer> elementalResistances) {
        return new ItemTemplate(UUID.randomUUID(), "armor", "description", ItemType.ARMOR, 5, ArmorCategory.LIGHT, 0, 0,
                5, 0, 0, 0, 0, 100, List.of(), elementalResistances, ItemGrade.D, null);
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
