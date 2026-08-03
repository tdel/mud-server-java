package fr.idev.mudserver.controller.ingame;

import java.util.Set;

import org.springframework.stereotype.Component;

import fr.idev.mudserver.controller.ControllerHandler;
import fr.idev.mudserver.game.GameWorld;
import fr.idev.mudserver.game.Client;
import fr.idev.mudserver.game.dice.DiceExpression;
import fr.idev.mudserver.game.dice.DiceRoller;
import fr.idev.mudserver.network.Connection;
import fr.idev.mudserver.network.ConnectionState;
import fr.idev.mudserver.network.message.Usage;
import fr.idev.mudserver.network.message.ingame.DiceRolled;

@Component
public class Roll implements ControllerHandler {

    private final DiceRoller diceRoller;
    private final GameWorld gameWorld;

    public Roll(DiceRoller diceRoller, GameWorld gameWorld) {
        this.diceRoller = diceRoller;
        this.gameWorld = gameWorld;
    }

    @Override
    public String name() {
        return "roll";
    }

    @Override
    public Set<ConnectionState> states() {
        return Set.of(ConnectionState.INGAME);
    }

    @Override
    public void onReceive(Connection session, String argument) {
        Client client = gameWorld.client(session);
        String notation = argument.trim();

        if (notation.isEmpty()) {
            client.send(new Usage("roll <XdY+Z>"));
            return;
        }

        DiceExpression expression;
        try {
            expression = DiceExpression.parse(notation);
        } catch (IllegalArgumentException e) {
            client.send(new Usage("roll <XdY+Z>"));
            return;
        }

        client.send(new DiceRolled(expression, diceRoller.roll(expression)));
    }
}
