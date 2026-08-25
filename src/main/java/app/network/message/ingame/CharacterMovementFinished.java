package app.network.message.ingame;

import app.network.OutputJsonMessage;

public record CharacterMovementFinished(String characterName, double x, double y) implements OutputJsonMessage {

}
