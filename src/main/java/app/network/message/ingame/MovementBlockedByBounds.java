package app.network.message.ingame;

import app.network.OutputJsonMessage;

public record MovementBlockedByBounds(double x, double y) implements OutputJsonMessage {

}
