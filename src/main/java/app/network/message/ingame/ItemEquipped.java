package app.network.message.ingame;

import java.util.UUID;

import app.network.OutputJsonMessage;
import app.domain.item.EquipmentSlot;
import app.domain.item.ItemGrade;

public record ItemEquipped(UUID itemId, String name, ItemGrade grade, EquipmentSlot slot) implements OutputJsonMessage {

}
