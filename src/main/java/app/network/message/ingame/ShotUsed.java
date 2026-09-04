package app.network.message.ingame;

import app.domain.item.ItemGrade;
import app.domain.item.ItemType;
import app.network.OutputJsonMessage;

public record ShotUsed(ItemType shotType, ItemGrade grade, int remainingQuantity) implements OutputJsonMessage {

}
