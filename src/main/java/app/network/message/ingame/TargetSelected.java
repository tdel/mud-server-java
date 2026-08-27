package app.network.message.ingame;

import java.util.UUID;

import app.network.OutputJsonMessage;

public record TargetSelected(UUID targetId, String targetName) implements OutputJsonMessage {

}
