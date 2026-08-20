package fr.idev.mudserver.network.command.ingame;

import java.util.Set;

import org.springframework.stereotype.Component;

import fr.idev.mudserver.network.CommandHandler;
import fr.idev.mudserver.game.dice.DiceExpression;
import fr.idev.mudserver.game.dice.DiceRoller;
import fr.idev.mudserver.network.Connection;
import fr.idev.mudserver.network.ConnectionState;
import fr.idev.mudserver.network.message.Usage;
import fr.idev.mudserver.network.message.ingame.DiceRolled;

@Component
public class Roll implements CommandHandler {

    @Override
    public String name() {
        return "roll";
    }

    @Override
    public Set<ConnectionState> states() {
        return Set.of(ConnectionState.INGAME);
    }

    @Override
    public void onReceive(Connection connection, String argument) {
        String notation = argument.trim();

        if (notation.isEmpty()) {
            connection.send(new Usage("roll <XdY+Z>"));
            return;
        }

        DiceExpression expression;
        try {
            expression = DiceExpression.parse(notation);
        } catch (IllegalArgumentException e) {
            connection.send(new Usage("roll <XdY+Z>"));
            return;
        }

        connection.send(new DiceRolled(expression, DiceRoller.roll(expression)));
    }
}
