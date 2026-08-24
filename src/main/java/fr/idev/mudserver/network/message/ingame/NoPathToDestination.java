package fr.idev.mudserver.network.message.ingame;

import fr.idev.mudserver.network.OutputJsonMessage;

public record NoPathToDestination(int q, int r) implements OutputJsonMessage {

}
