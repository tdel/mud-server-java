package app.network.message.ingame;

import app.network.OutputJsonMessage;

public record PeaceZoneExited(String zoneName) implements OutputJsonMessage {

}
