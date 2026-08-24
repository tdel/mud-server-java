package fr.idev.mudserver.network.message.ingame;

import fr.idev.mudserver.network.OutputJsonMessage;

public record MovementStarted(double x, double y) implements OutputJsonMessage {

}
