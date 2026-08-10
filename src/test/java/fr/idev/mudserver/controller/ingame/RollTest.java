package fr.idev.mudserver.controller.ingame;

import org.junit.jupiter.api.Test;

import fr.idev.mudserver.controller.RecordingConnection;
import fr.idev.mudserver.game.dice.DiceRoller;
import fr.idev.mudserver.network.message.Usage;
import fr.idev.mudserver.network.message.ingame.DiceRolled;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Aucune dépendance Spring/DB (contrairement aux autres {@code
 * ControllerHandler} : {@link Roll} ne dépend que de {@link DiceRoller}, classe
 * utilitaire statique, jamais un bean à injecter.
 */
class RollTest {

    private final Roll roll = new Roll();
    private final RecordingConnection connection = new RecordingConnection();

    @Test
    void emptyArgumentSendsUsage() {
        roll.onReceive(connection, "");

        assertThat(connection.received).containsExactly(new Usage("roll <XdY+Z>"));
    }

    @Test
    void invalidNotationSendsUsage() {
        roll.onReceive(connection, "notdice");

        assertThat(connection.received).containsExactly(new Usage("roll <XdY+Z>"));
    }

    @Test
    void validNotationSendsDiceRolledWithTheParsedExpression() {
        roll.onReceive(connection, "2d6+1");

        assertThat(connection.received).hasSize(1);
        DiceRolled rolled = (DiceRolled) connection.received.get(0);
        assertThat(rolled.expression().toString()).isEqualTo("2d6+1");
        assertThat(rolled.result().rolls()).hasSize(2);
        assertThat(rolled.result().total()).isBetween(3, 13);
    }
}
