package app.network.message.ingame;

import app.network.OutputJsonMessage;

public record CharacterUsedItem(String characterName, String itemName) implements OutputJsonMessage {

}
