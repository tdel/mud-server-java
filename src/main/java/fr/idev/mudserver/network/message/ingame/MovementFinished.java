package fr.idev.mudserver.network.message.ingame;

import fr.idev.mudserver.network.OutputJsonMessage;

public record MovementFinished(int q, int r) implements OutputJsonMessage {

}
