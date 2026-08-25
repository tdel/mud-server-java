package app.network.message.ingame;

import app.network.OutputJsonMessage;

public record CharacterMovementStopped(String characterName) implements OutputJsonMessage {

}
