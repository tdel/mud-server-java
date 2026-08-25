package app.network.message.ingame;

import app.network.OutputJsonMessage;

public record CharacterPositionUpdated(String characterName, double x, double y) implements OutputJsonMessage {

}
