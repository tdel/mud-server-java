package fr.idev.mudserver.network.message.ingame;

import fr.idev.mudserver.network.OutputJsonMessage;

public record PositionUpdated(double x, double y) implements OutputJsonMessage {

}
