package app.network.message.ingame;

import app.network.OutputJsonMessage;
import app.domain.item.Rarity;

public record EquipmentLooted(String itemName, Rarity rarity) implements OutputJsonMessage {

}
