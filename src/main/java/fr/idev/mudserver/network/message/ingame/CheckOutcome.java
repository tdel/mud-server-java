package fr.idev.mudserver.network.message.ingame;

import fr.idev.mudserver.network.OutputJsonMessage;
import fr.idev.mudserver.game.dice.CheckResult;

public record CheckOutcome(CheckResult result) implements OutputJsonMessage {

}
