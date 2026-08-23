package fr.idev.mudserver.domain.actor.instance;

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
import fr.idev.mudserver.domain.actor.Attribute;
import fr.idev.mudserver.domain.actor.CharacterClass;
import fr.idev.mudserver.domain.actor.Gender;
import fr.idev.mudserver.domain.actor.Race;
import fr.idev.mudserver.domain.actor.event.CharacterLeveledUp;
import fr.idev.mudserver.domain.actor.event.DomainEventPublisher;
import fr.idev.mudserver.domain.map.HexCoordinate;
import fr.idev.mudserver.domain.world.ZoneInstance;
import fr.idev.mudserver.domain.world.ZoneTemplate;
import fr.idev.mudserver.domain.world.TileType;
import fr.idev.mudserver.domain.world.WorldInstance;

class CharacterInstanceLevelUpTest {

    private final List<Object> publishedEvents = new ArrayList<>();

    @BeforeEach
    void setUpEventPublisher() {
        publishedEvents.clear();
        DomainEventPublisher.initialize(publishedEvents::add);
    }

    private CharacterInstance newFighter(int level, int currentHealth, int maxHealth) {
        WorldInstance world = new WorldInstance(UUID.randomUUID(), UUID.randomUUID(), Instant.now());
        ZoneTemplate zoneTemplate = new ZoneTemplate(UUID.randomUUID(), "Zone", "desc", true, flatTerrain(3, 3),
                new HexCoordinate(0, 0), List.of());
        ZoneInstance zone = new ZoneInstance(UUID.randomUUID(), zoneTemplate, world);
        Account account = new Account(UUID.randomUUID(), "login", "hash", null);

        Map<Attribute, Integer> attributes = new EnumMap<>(Attribute.class);
        for (Attribute attribute : Attribute.values()) {
            attributes.put(attribute, 14);
        }

        return new CharacterInstance(UUID.randomUUID(), account, "Hero", zone, Gender.MAN, Race.HUMAN,
                CharacterClass.FIGHTER, level, currentHealth, maxHealth, attributes, 0, 0);
    }

    private CharacterInstance newSorcerer(int level, int currentHealth, int maxHealth) {
        WorldInstance world = new WorldInstance(UUID.randomUUID(), UUID.randomUUID(), Instant.now());
        ZoneTemplate zoneTemplate = new ZoneTemplate(UUID.randomUUID(), "Zone", "desc", true, flatTerrain(3, 3),
                new HexCoordinate(0, 0), List.of());
        ZoneInstance zone = new ZoneInstance(UUID.randomUUID(), zoneTemplate, world);
        Account account = new Account(UUID.randomUUID(), "login", "hash", null);

        Map<Attribute, Integer> attributes = new EnumMap<>(Attribute.class);
        for (Attribute attribute : Attribute.values()) {
            attributes.put(attribute, 14);
        }

        return new CharacterInstance(UUID.randomUUID(), account, "Mage", zone, Gender.WOMAN, Race.HUMAN,
                CharacterClass.SORCERER, level, currentHealth, maxHealth, attributes, 0, 0);
    }

    @Test
    void hitDieRecoveryMatchesFighterHitDieAndConstitutionModifier() {
        CharacterInstance character = newFighter(1, 10, 10);

        // FIGHTER hitDie=10, modificateur CON à 14 = +2 -> 10/2 + 1 + 2 = 8
        assertThat(character.hitDieRecovery()).isEqualTo(8);
    }

    @Test
    void applyLevelUpIncrementsLevelAndHealthThenPublishesEvent() {
        CharacterInstance character = newFighter(1, 10, 10);
        int hpGain = character.hitDieRecovery();

        character.applyLevelUp();

        assertThat(character.getLevel()).isEqualTo(2);
        assertThat(character.getMaxHealth()).isEqualTo(10 + hpGain);
        assertThat(character.getCurrentHealth()).isEqualTo(10 + hpGain);
        assertThat(publishedEvents).hasSize(1);
        assertThat(publishedEvents.get(0)).isInstanceOfSatisfying(CharacterLeveledUp.class, event -> {
            assertThat(event.newLevel()).isEqualTo(2);
            assertThat(event.hpGained()).isEqualTo(hpGain);
        });
    }

    @Test
    void applyLevelUpCalledTwiceStacksTwoLevels() {
        CharacterInstance character = newFighter(1, 10, 10);

        character.applyLevelUp();
        character.applyLevelUp();

        assertThat(character.getLevel()).isEqualTo(3);
        assertThat(publishedEvents).hasSize(2);
    }

    @Test
    void applyLevelUpDoesNotIncrementManaForNonCasterClass() {
        CharacterInstance character = newFighter(1, 10, 10);

        character.applyLevelUp();

        assertThat(character.getMaxMana()).isZero();
        assertThat(character.getCurrentMana()).isZero();
    }

    @Test
    void applyLevelUpIncrementsManaForCasterClass() {
        CharacterInstance character = newSorcerer(1, 10, 10);

        character.applyLevelUp();

        assertThat(character.getMaxMana()).isEqualTo(CharacterClass.SORCERER.manaGainPerLevel());
        assertThat(character.getCurrentMana()).isEqualTo(CharacterClass.SORCERER.manaGainPerLevel());
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
