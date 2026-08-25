package app.network.message.ingame;

import app.network.OutputJsonMessage;

public record MovementFinished(double x, double y) implements OutputJsonMessage {

}
