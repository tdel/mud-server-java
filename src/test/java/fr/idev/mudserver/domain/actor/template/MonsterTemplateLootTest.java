package fr.idev.mudserver.domain.actor.template;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import fr.idev.mudserver.domain.Account;
import fr.idev.mudserver.domain.actor.Attribute;
import fr.idev.mudserver.domain.actor.CharacterClass;
import fr.idev.mudserver.domain.actor.Gender;
import fr.idev.mudserver.domain.actor.Race;
import fr.idev.mudserver.domain.actor.event.DomainEventPublisher;
import fr.idev.mudserver.domain.actor.instance.CharacterInstance;
import fr.idev.mudserver.domain.actor.template.MonsterTemplate.LootResult;
import fr.idev.mudserver.domain.actor.template.MonsterTemplate.LootTableEntry;
import fr.idev.mudserver.domain.item.ItemTemplate;
import fr.idev.mudserver.domain.item.ItemType;
import fr.idev.mudserver.domain.item.Rarity;
import fr.idev.mudserver.domain.map.HexCoordinate;
import fr.idev.mudserver.domain.world.RoomInstance;
import fr.idev.mudserver.domain.world.RoomTemplate;
import fr.idev.mudserver.domain.world.WorldInstance;

class MonsterTemplateLootTest {

    @BeforeEach
    void setUpEventPublisher() {
        DomainEventPublisher.initialize(event -> {
        });
    }

    private CharacterInstance newKiller() {
        WorldInstance world = new WorldInstance(UUID.randomUUID(), UUID.randomUUID(), Instant.now());
        RoomTemplate roomTemplate = new RoomTemplate(UUID.randomUUID(), "Room", "desc", true, 3, 3,
                new HexCoordinate(0, 0), List.of());
        RoomInstance room = new RoomInstance(UUID.randomUUID(), roomTemplate, world);
        Account account = new Account(UUID.randomUUID(), "login", "hash", null);

        Map<Attribute, Integer> attributes = new EnumMap<>(Attribute.class);
        for (Attribute attribute : Attribute.values()) {
            attributes.put(attribute, 14);
        }

        return new CharacterInstance(UUID.randomUUID(), account, "Hero", room, Gender.MAN, Race.HUMAN,
                CharacterClass.FIGHTER, 1, 10, 10, attributes, 0, 0);
    }

    private ItemTemplate newItemTemplate() {
        return new ItemTemplate(UUID.randomUUID(), "Potion", "desc", ItemType.MISC, 1, null, 0, null, null, 10,
                Rarity.COMMON, 0);
    }

    @Test
    void rollLootAlwaysGrantsGoldAndCertainDrops() {
        ItemTemplate certainDrop = newItemTemplate();
        ItemTemplate impossibleDrop = newItemTemplate();
        MonsterTemplate template = new MonsterTemplate(UUID.randomUUID(), "Goblin", "desc", 7, Map.of(), 13, 50, "1d6",
                25, List.of(new LootTableEntry(certainDrop, 100), new LootTableEntry(impossibleDrop, 0)), 3, 5, 1);

        LootResult loot = template.rollLoot(newKiller());

        assertThat(loot.gold()).isEqualTo(25);
        assertThat(loot.items()).extracting(item -> item.getTemplate().getId()).containsExactly(certainDrop.getId());
    }

    @Test
    void rollLootGrantsNoGoldWhenTemplateHasNoGoldReward() {
        MonsterTemplate template = new MonsterTemplate(UUID.randomUUID(), "Rat", "desc", 2, Map.of(), 10, 5, "1d4", 0,
                List.of(), 1, 5, 1);

        LootResult loot = template.rollLoot(newKiller());

        assertThat(loot.gold()).isZero();
        assertThat(loot.items()).isEmpty();
    }
}
