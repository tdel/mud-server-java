package app.network.message.ingame;

import app.network.OutputJsonMessage;

public record CharacterMovementBlocked(String characterName) implements OutputJsonMessage {

}
