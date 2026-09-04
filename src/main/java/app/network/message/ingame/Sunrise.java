package app.network.message.ingame;

import app.network.OutputJsonMessage;

public record Sunrise(int hour, int minute, long transitionDurationMs) implements OutputJsonMessage {

}
