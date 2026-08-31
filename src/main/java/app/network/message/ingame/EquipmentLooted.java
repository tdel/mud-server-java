package app.network.message.ingame;

import app.network.OutputJsonMessage;
import app.domain.item.ItemGrade;

public record EquipmentLooted(String itemName, ItemGrade grade) implements OutputJsonMessage {

}
