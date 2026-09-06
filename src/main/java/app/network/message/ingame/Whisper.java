package app.network.message.ingame;

import app.network.OutputJsonMessage;

public record Whisper(String fromName, String toName, String text) implements OutputJsonMessage {

}
