package app.network.message.ingame;

import app.network.OutputJsonMessage;

public record WhisperTargetNotFound(String name) implements OutputJsonMessage {

}
