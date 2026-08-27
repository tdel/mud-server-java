package app.network.message.ingame;

import java.util.UUID;

import app.network.OutputJsonMessage;
import app.domain.item.EquipmentSlot;
import app.domain.item.Rarity;

public record ItemEquipped(UUID itemId, String name, Rarity rarity, EquipmentSlot slot) implements OutputJsonMessage {

}
