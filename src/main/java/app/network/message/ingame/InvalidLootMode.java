package app.network.message.ingame;

import app.network.OutputJsonMessage;

public record InvalidLootMode(String argument) implements OutputJsonMessage {

}
