package fr.idev.mudserver.domain.actor.instance;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import fr.idev.mudserver.domain.Account;
import fr.idev.mudserver.domain.Spell;
import fr.idev.mudserver.domain.SpellEffectType;
import fr.idev.mudserver.domain.actor.Attribute;
import fr.idev.mudserver.domain.actor.CharacterClass;
import fr.idev.mudserver.domain.actor.Gender;
import fr.idev.mudserver.domain.actor.Race;
import fr.idev.mudserver.domain.actor.event.DomainEventPublisher;
import fr.idev.mudserver.domain.item.Item;
import fr.idev.mudserver.domain.item.ItemTemplate;
import fr.idev.mudserver.domain.item.ItemType;
import fr.idev.mudserver.domain.item.Rarity;
import fr.idev.mudserver.domain.map.HexCoordinate;
import fr.idev.mudserver.domain.world.RoomInstance;
import fr.idev.mudserver.domain.world.RoomTemplate;
import fr.idev.mudserver.domain.world.WorldInstance;

class CharacterInstanceGrantedSpellsTest {

    @BeforeEach
    void setUpEventPublisher() {
        DomainEventPublisher.initialize(event -> {
        });
    }

    private CharacterInstance newCharacter() {
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

    private Spell spell(String name) {
        return new Spell(UUID.randomUUID(), name, "desc", 1, 3, 3, 6, SpellEffectType.DAMAGE, "1d4", Set.of(), null, 0);
    }

    private Item newItem(ItemType type, List<Spell> grantedSpells) {
        ItemTemplate template = new ItemTemplate(UUID.randomUUID(), "Objet magique", "desc", type, 1, null, 0, "1d4",
                null, 100, Rarity.UNCOMMON, 0, grantedSpells);
        return new Item(UUID.randomUUID(), template, null, null);
    }

    @Test
    void equippingItemsGrantsTheirSpellsAndUnequippingRevokesThem() {
        CharacterInstance character = newCharacter();
        Spell fireBolt = spell("Fire Bolt");
        Spell bless = spell("Bless");
        Item weapon = newItem(ItemType.WEAPON, List.of(fireBolt));
        Item armor = newItem(ItemType.ARMOR, List.of(bless));

        character.getInventory().addItem(weapon);
        character.getInventory().addItem(armor);
        character.equipItem(weapon);
        character.equipItem(armor);

        assertThat(character.getGrantedSpells()).containsExactlyInAnyOrder(fireBolt, bless);

        character.unequipItem(weapon);

        assertThat(character.getGrantedSpells()).containsExactly(bless);
    }

    @Test
    void spellsFromUnequippedItemsAreNeverGranted() {
        CharacterInstance character = newCharacter();
        Item weapon = newItem(ItemType.WEAPON, List.of(spell("Fire Bolt")));

        character.getInventory().addItem(weapon);

        assertThat(character.getGrantedSpells()).isEmpty();
    }
}
