package app.network.message.ingame;

import app.network.OutputJsonMessage;

public record GamePlayerJoinedZone(String characterName) implements OutputJsonMessage {

}
