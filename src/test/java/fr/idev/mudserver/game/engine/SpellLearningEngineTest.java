package fr.idev.mudserver.game.engine;

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
import fr.idev.mudserver.domain.Spell;
import fr.idev.mudserver.domain.actor.Attribute;
import fr.idev.mudserver.domain.actor.CharacterClass;
import fr.idev.mudserver.domain.actor.Gender;
import fr.idev.mudserver.domain.actor.Race;
import fr.idev.mudserver.domain.actor.event.CharacterLearnedSpell;
import fr.idev.mudserver.domain.actor.event.CharacterLeveledUp;
import fr.idev.mudserver.domain.actor.event.DomainEventPublisher;
import fr.idev.mudserver.domain.actor.event.NewGamePlayerCreated;
import fr.idev.mudserver.domain.actor.instance.CharacterInstance;
import fr.idev.mudserver.domain.map.HexCoordinate;
import fr.idev.mudserver.domain.world.ZoneInstance;
import fr.idev.mudserver.domain.world.ZoneTemplate;
import fr.idev.mudserver.domain.world.WorldInstance;
import fr.idev.mudserver.game.catalog.SpellCatalog;
import fr.idev.mudserver.game.catalog.SpellCatalogHolder;
import tools.jackson.databind.ObjectMapper;

class SpellLearningEngineTest {

    private final List<Object> publishedEvents = new ArrayList<>();
    private final SpellLearningEngine engine = new SpellLearningEngine();
    private SpellCatalog catalog;

    @BeforeEach
    void setUp() {
        publishedEvents.clear();
        DomainEventPublisher.initialize(publishedEvents::add);

        catalog = new SpellCatalog(new ObjectMapper());
        catalog.warmSpells();
        SpellCatalogHolder.initialize(catalog);
    }

    private CharacterInstance newSorcerer(int level) {
        WorldInstance world = new WorldInstance(UUID.randomUUID(), UUID.randomUUID(), Instant.now());
        ZoneTemplate zoneTemplate = new ZoneTemplate(UUID.randomUUID(), "Zone", "desc", true, 3, 3,
                new HexCoordinate(0, 0), List.of());
        ZoneInstance zone = new ZoneInstance(UUID.randomUUID(), zoneTemplate, world);
        Account account = new Account(UUID.randomUUID(), "login", "hash", null);

        Map<Attribute, Integer> attributes = new EnumMap<>(Attribute.class);
        for (Attribute attribute : Attribute.values()) {
            attributes.put(attribute, 14);
        }

        return new CharacterInstance(UUID.randomUUID(), account, "Mage", zone, Gender.WOMAN, Race.HUMAN,
                CharacterClass.SORCERER, level, 10, 10, attributes, 0, 0);
    }

    @Test
    void onCharacterLeveledUpLearnsSpellsAtRequiredLevelAndPublishesEvent() {
        Spell scorchingRay = catalog.allSpells().stream().filter(spell -> spell.name().equals("Scorching Ray"))
                .findFirst().orElseThrow();

        CharacterInstance character = newSorcerer(2);

        engine.onCharacterLeveledUp(new CharacterLeveledUp(character, 3, 5));

        assertThat(character.getSpellCasting().knows(scorchingRay.id())).isTrue();
        assertThat(publishedEvents)
                .anySatisfy(event -> assertThat(event).isInstanceOfSatisfying(CharacterLearnedSpell.class,
                        learned -> assertThat(learned.spell().name()).isEqualTo("Scorching Ray")));
    }

    @Test
    void onNewGamePlayerCreatedLearnsLevelOneSpells() {
        CharacterInstance character = newSorcerer(1);

        engine.onNewGamePlayerCreated(new NewGamePlayerCreated(character));

        assertThat(publishedEvents)
                .anySatisfy(event -> assertThat(event).isInstanceOfSatisfying(CharacterLearnedSpell.class,
                        learned -> assertThat(learned.spell().requiredLevel()).isEqualTo(1)));
    }
}
