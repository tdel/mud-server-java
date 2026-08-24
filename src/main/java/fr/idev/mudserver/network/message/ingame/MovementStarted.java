package fr.idev.mudserver.network.message.ingame;

import fr.idev.mudserver.network.OutputJsonMessage;

public record MovementStarted(int q, int r) implements OutputJsonMessage {

}
