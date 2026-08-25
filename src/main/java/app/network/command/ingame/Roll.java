package app.network.command.ingame;

import java.util.Set;

import org.springframework.stereotype.Component;

import app.network.CommandHandler;
import app.game.dice.DiceExpression;
import app.game.dice.DiceRoller;
import app.network.Connection;
import app.network.ConnectionState;
import app.network.message.Usage;
import app.network.message.ingame.DiceRolled;

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
