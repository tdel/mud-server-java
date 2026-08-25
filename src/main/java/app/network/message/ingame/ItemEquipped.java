package app.network.message.ingame;

import app.network.OutputJsonMessage;
import app.domain.item.EquipmentSlot;
import app.domain.item.Rarity;

public record ItemEquipped(String name, Rarity rarity, EquipmentSlot slot) implements OutputJsonMessage {

}
