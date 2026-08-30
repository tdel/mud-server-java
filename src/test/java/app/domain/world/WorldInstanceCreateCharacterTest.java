package app.domain.world;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import app.domain.Account;
import app.domain.actor.Attribute;
import app.domain.actor.CharacterClass;
import app.domain.actor.Gender;
import app.domain.actor.Race;
import app.domain.actor.event.DomainEventPublisher;
import app.domain.actor.instance.CharacterInstance;
import app.domain.map.Position;

class WorldInstanceCreateCharacterTest {

    @BeforeAll
    static void initDomainEventPublisher() {
        DomainEventPublisher.initialize(event -> {
        });
    }

    @Test
    void createCharacterAppliesFighterBaseAttributesPlusRacialBonus() {
        CharacterInstance fighter = createCharacter(CharacterClass.FIGHTER);
        assertAttributesMatchClassProfilePlusHumanBonus(fighter, CharacterClass.FIGHTER);
    }

    @Test
    void createCharacterAppliesMysticBaseAttributesPlusRacialBonus() {
        CharacterInstance mystic = createCharacter(CharacterClass.MYSTIC);
        assertAttributesMatchClassProfilePlusHumanBonus(mystic, CharacterClass.MYSTIC);
    }

    @Test
    void creatingTwoCharactersOfTheSameClassGivesIdenticalAttributes() {
        CharacterInstance first = createCharacter(CharacterClass.FIGHTER);
        CharacterInstance second = createCharacter(CharacterClass.FIGHTER);

        assertThat(second.getAttributes()).isEqualTo(first.getAttributes());
    }

    private void assertAttributesMatchClassProfilePlusHumanBonus(CharacterInstance character,
            CharacterClass characterClass) {
        for (Attribute attribute : Attribute.values()) {
            int expected = characterClass.baseAttributes().get(attribute) + Race.HUMAN.attributeScoreBonuses()
                    .getOrDefault(attribute, 0);
            assertThat(character.getAttribute(attribute)).as("attribute %s", attribute).isEqualTo(expected);
        }
    }

    private CharacterInstance createCharacter(CharacterClass characterClass) {
        WorldInstance world = worldWithStartingMap();
        Account account = new Account(UUID.randomUUID(), "login", "hash");
        return world.createCharacter(account, "hero", Gender.MAN, Race.HUMAN, characterClass);
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
