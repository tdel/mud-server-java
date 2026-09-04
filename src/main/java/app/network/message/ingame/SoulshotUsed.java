package app.network.message.ingame;

import java.util.UUID;

import app.domain.item.ItemGrade;
import app.network.OutputJsonMessage;

public record SoulshotUsed(UUID characterId, String characterName, ItemGrade grade) implements OutputJsonMessage {

}
