package app.network.message.ingame;

import app.network.OutputJsonMessage;
import app.game.dice.CheckResult;

public record CheckOutcome(CheckResult result) implements OutputJsonMessage {

}
