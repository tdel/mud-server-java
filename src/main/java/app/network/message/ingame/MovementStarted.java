package app.network.message.ingame;

import app.network.OutputJsonMessage;

public record MovementStarted(double x, double y, double heading) implements OutputJsonMessage {

}
