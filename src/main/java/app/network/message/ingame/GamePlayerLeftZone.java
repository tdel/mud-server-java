package app.network.message.ingame;

import app.network.OutputJsonMessage;

public record GamePlayerLeftZone(String characterName) implements OutputJsonMessage {

}
