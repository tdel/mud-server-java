package fr.idev.mudserver.network.action.ingame;

import java.util.Set;

import org.springframework.stereotype.Component;

import fr.idev.mudserver.game.PlayerInstance;
import fr.idev.mudserver.game.dice.DiceExpression;
import fr.idev.mudserver.game.dice.DiceRoller;
import fr.idev.mudserver.network.ActionHandler;
import fr.idev.mudserver.network.ConnectionState;
import fr.idev.mudserver.network.Session;
import fr.idev.mudserver.network.message.Usage;
import fr.idev.mudserver.network.message.ingame.DiceRolled;

@Component
public class Roll implements ActionHandler {

    private final DiceRoller diceRoller;

    public Roll(DiceRoller diceRoller) {
        this.diceRoller = diceRoller;
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
    public void onReceive(Session session, String argument) {
        PlayerInstance player = session.player();
        String notation = argument.trim();

        if (notation.isEmpty()) {
            player.send(new Usage("roll <XdY+Z>"));
            return;
        }

        DiceExpression expression;
        try {
            expression = DiceExpression.parse(notation);
        } catch (IllegalArgumentException e) {
            player.send(new Usage("roll <XdY+Z>"));
            return;
        }

        player.send(new DiceRolled(expression, diceRoller.roll(expression)));
    }
}
