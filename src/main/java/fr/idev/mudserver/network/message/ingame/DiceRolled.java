package fr.idev.mudserver.network.message.ingame;

import fr.idev.mudserver.network.OutputJsonMessage;
import fr.idev.mudserver.game.dice.DiceExpression;
import fr.idev.mudserver.game.dice.DiceRoll;

public record DiceRolled(DiceExpression expression, DiceRoll result) implements OutputJsonMessage {

}
