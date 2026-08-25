package app.network.message.ingame;

import app.network.OutputJsonMessage;

public record GamePlayerDisconnected(String characterName) implements OutputJsonMessage {

}
