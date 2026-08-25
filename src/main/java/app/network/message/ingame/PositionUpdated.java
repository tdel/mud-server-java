package app.network.message.ingame;

import app.network.OutputJsonMessage;

public record PositionUpdated(double x, double y) implements OutputJsonMessage {

}
