package app.network.message.ingame;

import app.network.OutputJsonMessage;

public record PeaceZoneEntered(String zoneName, String description) implements OutputJsonMessage {

}
