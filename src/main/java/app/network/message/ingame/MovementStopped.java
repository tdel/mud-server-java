package app.network.message.ingame;

import app.network.OutputJsonMessage;

public record MovementStopped(double x, double y) implements OutputJsonMessage {

}
