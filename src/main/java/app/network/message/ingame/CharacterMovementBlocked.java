package app.network.message.ingame;

import java.util.UUID;

import app.network.OutputJsonMessage;

public record CharacterMovementBlocked(UUID characterId, String characterName, double x,
        double y) implements OutputJsonMessage {

}
