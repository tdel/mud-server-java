package app.network.message.ingame;

import app.network.OutputJsonMessage;
import app.game.dice.DiceExpression;
import app.game.dice.DiceRoll;

public record DiceRolled(DiceExpression expression, DiceRoll result) implements OutputJsonMessage {

}
