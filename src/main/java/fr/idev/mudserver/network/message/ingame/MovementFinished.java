package fr.idev.mudserver.network.message.ingame;

import fr.idev.mudserver.network.OutputJsonMessage;

public record MovementFinished(double x, double y) implements OutputJsonMessage {

}
