package app.network.message.ingame;

import app.network.OutputJsonMessage;

public record PlayerRespawned(String zoneName) implements OutputJsonMessage {

}
