package fr.idev.mudserver.domain;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import fr.idev.mudserver.AbstractIntegrationTest;
import fr.idev.mudserver.domain.actor.CharacterClass;
import fr.idev.mudserver.domain.actor.Gender;
import fr.idev.mudserver.domain.actor.GamePlayer;
import fr.idev.mudserver.domain.actor.Race;
import fr.idev.mudserver.domain.actor.TestAttributes;
import fr.idev.mudserver.domain.actor.TestProficiencies;
import fr.idev.mudserver.game.dice.DiceRoller;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contexte Spring requis (pas un test JUnit pur) : {@code consume()} publie
 * {@link fr.idev.mudserver.domain.actor.event.GamePlayerUsedPotion} via le
 * holder statique {@code DomainEventPublisher}, qui suppose son
 * {@code ApplicationEventPublisher} déjà initialisé — l'initialiser
 * manuellement dans un test JUnit pur risquerait de polluer les autres tests du
 * même run (JVM partagée par Surefire). Le personnage et l'objet ne sont pas
 * persistés : les listeners ({@code CharacterService}/{@code ItemService})
 * tolèrent un UPDATE/DELETE sans ligne correspondante (no-op), donc aucune
 * insertion préalable n'est nécessaire pour ces assertions.
 */
@Transactional
class ConsumableItemTest extends AbstractIntegrationTest {

    @Autowired
    private DiceRoller diceRoller;

    @Test
    void consumeHealsWithinTheEffectDiceRangeAndRemovesTheItem() {
        GamePlayer character = character(5, 50);
        Item item = healingPotion("2d4+2");
        character.getInventory().addItem(item);
        ConsumableItem template = (ConsumableItem) item.getTemplate();

        template.consume(character, item);

        assertThat(character.getCurrentHealth()).isBetween(5 + 4, 5 + 10);
        assertThat(character.getInventory().getItems()).doesNotContain(item);
    }

    @Test
    void consumeClampsHealingAtMaxHealthWithoutOverhealing() {
        GamePlayer character = character(50, 50);
        Item item = healingPotion("2d4+2");
        character.getInventory().addItem(item);
        ConsumableItem template = (ConsumableItem) item.getTemplate();

        template.consume(character, item);

        assertThat(character.getCurrentHealth()).isEqualTo(50);
        assertThat(character.getInventory().getItems()).doesNotContain(item);
    }

    private Item healingPotion(String effectDice) {
        ConsumableItem template = new ConsumableItem(UUID.randomUUID(), "Potion de soin", null, ItemType.POTION, 1,
                null, 0, null, 50, Rarity.COMMON, 0, ConsumableEffect.HEALING, effectDice, diceRoller);
        Item item = new Item(UUID.randomUUID(), template.getId(), null, null, null);
        item.attachTemplate(template);
        return item;
    }

    private GamePlayer character(int currentHealth, int maxHealth) {
        return new GamePlayer(UUID.randomUUID(), UUID.randomUUID(), "Test", UUID.randomUUID(), Gender.MAN, Race.HUMAN,
                CharacterClass.FIGHTER, TestProficiencies.savingThrows(CharacterClass.FIGHTER),
                TestProficiencies.skills(CharacterClass.FIGHTER), 1, currentHealth, maxHealth,
                TestAttributes.of(10, 10, 10, 10, 10, 10), 0, 0);
    }
}
