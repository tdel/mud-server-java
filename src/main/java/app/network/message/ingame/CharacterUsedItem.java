package app.network.message.ingame;

import java.util.UUID;

import app.network.OutputJsonMessage;

public record CharacterUsedItem(UUID characterId, String characterName, UUID itemId,
        String itemName) implements OutputJsonMessage {

}
