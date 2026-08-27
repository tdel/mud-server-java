package app.network.message.ingame;

import app.network.OutputJsonMessage;

public record TargetNotFound(String targetId) implements OutputJsonMessage {

}
