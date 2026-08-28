package app.network.message.ingame;

import java.util.UUID;

import app.network.OutputJsonMessage;

public record CharacterMovementFinished(UUID characterId, String characterName, double x,
        double y) implements OutputJsonMessage {

}
