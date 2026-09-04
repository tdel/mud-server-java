package app.network.message.ingame;

import app.network.OutputJsonMessage;

public record Sunset(int hour, int minute, long transitionDurationMs) implements OutputJsonMessage {

}
