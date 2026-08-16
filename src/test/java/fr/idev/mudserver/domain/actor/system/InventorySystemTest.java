package fr.idev.mudserver.domain.actor.system;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import fr.idev.mudserver.domain.Account;
import fr.idev.mudserver.domain.actor.Attribute;
import fr.idev.mudserver.domain.actor.CharacterClass;
import fr.idev.mudserver.domain.actor.Gender;
import fr.idev.mudserver.domain.actor.Race;
import fr.idev.mudserver.domain.actor.event.DomainEventPublisher;
import fr.idev.mudserver.domain.actor.event.GamePlayerEquippedItem;
import fr.idev.mudserver.domain.actor.event.GamePlayerUnequippedItem;
import fr.idev.mudserver.domain.actor.event.ItemDiscarded;
import fr.idev.mudserver.domain.actor.instance.CharacterInstance;
import fr.idev.mudserver.domain.item.EquipmentSlot;
import fr.idev.mudserver.domain.item.Item;
import fr.idev.mudserver.domain.item.ItemTemplate;
import fr.idev.mudserver.domain.item.ItemType;
import fr.idev.mudserver.domain.item.Rarity;
import fr.idev.mudserver.domain.map.HexCoordinate;
import fr.idev.mudserver.domain.world.RoomInstance;
import fr.idev.mudserver.domain.world.RoomTemplate;
import fr.idev.mudserver.domain.world.WorldInstance;

class InventorySystemTest {

    private final List<Object> publishedEvents = new ArrayList<>();

    @BeforeEach
    void setUpEventPublisher() {
        publishedEvents.clear();
        DomainEventPublisher.initialize(publishedEvents::add);
    }

    private CharacterInstance newFighter(int gold) {
        WorldInstance world = new WorldInstance(UUID.randomUUID(), UUID.randomUUID(), Instant.now(), UUID.randomUUID(),
                Set.of());
        RoomTemplate roomTemplate = new RoomTemplate(UUID.randomUUID(), "Room", "desc", true, 3, 3,
                new HexCoordinate(0, 0), List.of());
        RoomInstance room = new RoomInstance(UUID.randomUUID(), roomTemplate, world);
        Account account = new Account(UUID.randomUUID(), "login", "hash", null);

        Map<Attribute, Integer> attributes = new EnumMap<>(Attribute.class);
        for (Attribute attribute : Attribute.values()) {
            attributes.put(attribute, 14);
        }

        return new CharacterInstance(UUID.randomUUID(), account, "Hero", room, Gender.MAN, Race.HUMAN,
                CharacterClass.FIGHTER, 1, 10, 10, attributes, 0, gold);
    }

    private Item newSword() {
        ItemTemplate template = new ItemTemplate(UUID.randomUUID(), "Sword", "desc", ItemType.WEAPON, 1, null, 0, "1d8",
                null, 10, Rarity.COMMON, 0);
        return new Item(UUID.randomUUID(), template, null, null);
    }

    @Test
    void trySpendGoldDebitsWhenSufficient() {
        CharacterInstance character = newFighter(100);

        assertThat(InventorySystem.trySpendGold(character, 40)).isTrue();
        assertThat(character.getGold()).isEqualTo(60);
    }

    @Test
    void trySpendGoldFailsAndLeavesGoldUntouchedWhenInsufficient() {
        CharacterInstance character = newFighter(10);

        assertThat(InventorySystem.trySpendGold(character, 40)).isFalse();
        assertThat(character.getGold()).isEqualTo(10);
    }

    @Test
    void addItemThenRemoveItemUpdatesItems() {
        CharacterInstance character = newFighter(0);
        Item sword = newSword();

        InventorySystem.addItem(character, sword);
        assertThat(character.getItems()).containsExactly(sword);

        InventorySystem.removeItem(character, sword);
        assertThat(character.getItems()).isEmpty();
    }

    @Test
    void equipSetsSlotAndPublishesGamePlayerEquippedItem() {
        CharacterInstance character = newFighter(0);
        Item sword = newSword();
        InventorySystem.addItem(character, sword);

        InventorySystem.equip(character, sword);

        assertThat(sword.getSlot()).isEqualTo(EquipmentSlot.WEAPON);
        assertThat(publishedEvents).singleElement().isInstanceOf(GamePlayerEquippedItem.class);
    }

    @Test
    void unequipClearsSlotAndPublishesGamePlayerUnequippedItem() {
        CharacterInstance character = newFighter(0);
        Item sword = newSword();
        InventorySystem.addItem(character, sword);
        InventorySystem.equip(character, sword);
        publishedEvents.clear();

        InventorySystem.unequip(character, sword);

        assertThat(sword.getSlot()).isNull();
        assertThat(publishedEvents).singleElement().isInstanceOf(GamePlayerUnequippedItem.class);
    }

    @Test
    void discardRemovesItemAndPublishesItemDiscarded() {
        CharacterInstance character = newFighter(0);
        Item sword = newSword();
        InventorySystem.addItem(character, sword);

        InventorySystem.discard(character, sword);

        assertThat(character.getItems()).isEmpty();
        assertThat(publishedEvents).singleElement().isInstanceOf(ItemDiscarded.class);
    }
}
