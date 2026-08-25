package app.network.message.ingame;

import app.network.OutputJsonMessage;

public record NoPathToDestination(double x, double y) implements OutputJsonMessage {

}
