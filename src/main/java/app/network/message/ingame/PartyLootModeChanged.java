package app.network.message.ingame;

import app.network.OutputJsonMessage;

public record PartyLootModeChanged(String lootMode) implements OutputJsonMessage {

}
