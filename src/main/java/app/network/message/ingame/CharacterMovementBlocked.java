package app.network.message.ingame;

import app.network.OutputJsonMessage;

public record CharacterMovementBlocked(String characterName, double x, double y) implements OutputJsonMessage {

}
