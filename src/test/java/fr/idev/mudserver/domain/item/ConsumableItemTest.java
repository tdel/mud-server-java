package fr.idev.mudserver.domain.item;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import fr.idev.mudserver.domain.Account;
import fr.idev.mudserver.domain.ConsumableEffect;
import fr.idev.mudserver.domain.actor.Attribute;
import fr.idev.mudserver.domain.actor.CharacterClass;
import fr.idev.mudserver.domain.actor.Gender;
import fr.idev.mudserver.domain.actor.Race;
import fr.idev.mudserver.domain.actor.event.DomainEventPublisher;
import fr.idev.mudserver.domain.actor.event.GamePlayerUsedManaPotion;
import fr.idev.mudserver.domain.actor.instance.CharacterInstance;
import fr.idev.mudserver.domain.map.HexCoordinate;
import fr.idev.mudserver.domain.world.ZoneInstance;
import fr.idev.mudserver.domain.world.ZoneTemplate;
import fr.idev.mudserver.domain.world.TileType;
import fr.idev.mudserver.domain.world.WorldInstance;

class ConsumableItemTest {

    private final List<Object> publishedEvents = new ArrayList<>();

    @BeforeEach
    void setUpEventPublisher() {
        publishedEvents.clear();
        DomainEventPublisher.initialize(publishedEvents::add);
    }

    private CharacterInstance newCharacter() {
        WorldInstance world = new WorldInstance(UUID.randomUUID(), UUID.randomUUID(), Instant.now());
        ZoneTemplate zoneTemplate = new ZoneTemplate(UUID.randomUUID(), "Zone", "desc", true, flatTerrain(3, 3),
                new HexCoordinate(0, 0), List.of(), List.of());
        ZoneInstance zone = new ZoneInstance(UUID.randomUUID(), zoneTemplate, world);
        Account account = new Account(UUID.randomUUID(), "login", "hash");

        Map<Attribute, Integer> attributes = new EnumMap<>(Attribute.class);
        for (Attribute attribute : Attribute.values()) {
            attributes.put(attribute, 14);
        }

        return new CharacterInstance(UUID.randomUUID(), account, "Mage", zone, Gender.MAN, Race.HUMAN,
                CharacterClass.WIZARD, 1, 10, 10, attributes, 0, 0, 0, 10, 4);
    }

    private ConsumableItem manaPotion(String effectDice) {
        return new ConsumableItem(UUID.randomUUID(), "Potion de mana", "desc", ItemType.POTION, 1, null, 0, null, null,
                60, Rarity.COMMON, 0, List.of(), ConsumableEffect.MANA_RESTORE, effectDice);
    }

    @Test
    void consumeManaRestoreRestoresManaAndPublishesEvent() {
        CharacterInstance character = newCharacter();
        ConsumableItem potion = manaPotion("1d1+3");
        Item item = new Item(UUID.randomUUID(), potion, character, null);
        character.getInventory().addItem(item);

        potion.consume(character, item);

        assertThat(character.getCurrentMana()).isEqualTo(8);
        assertThat(character.getInventory().getItems()).doesNotContain(item);
        assertThat(publishedEvents).hasSize(1);
        assertThat(publishedEvents.get(0)).isInstanceOfSatisfying(GamePlayerUsedManaPotion.class,
                event -> assertThat(event.restoredAmount()).isEqualTo(4));
    }

    @Test
    void consumeManaRestoreClampsToMaxMana() {
        CharacterInstance character = newCharacter();
        ConsumableItem potion = manaPotion("10d10+10");
        Item item = new Item(UUID.randomUUID(), potion, character, null);
        character.getInventory().addItem(item);

        potion.consume(character, item);

        assertThat(character.getCurrentMana()).isEqualTo(character.getMaxMana());
        assertThat(publishedEvents.get(0)).isInstanceOfSatisfying(GamePlayerUsedManaPotion.class,
                event -> assertThat(event.restoredAmount()).isEqualTo(6));
    }
    private static Map<HexCoordinate, TileType> flatTerrain(int width, int height) {
        Map<HexCoordinate, TileType> terrain = new java.util.HashMap<>();
        for (int q = 0; q < width; q++) {
            for (int r = 0; r < height; r++) {
                terrain.put(new HexCoordinate(q, r), TileType.FLOOR);
            }
        }
        return terrain;
    }
}
