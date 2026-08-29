package app.network.message.ingame;

import app.network.OutputJsonMessage;

public record GamePlayerLeftMap(String characterName) implements OutputJsonMessage {

}
