package app.network.message.ingame;

import java.util.List;

import app.network.OutputJsonMessage;
import app.domain.item.EquipmentSlot;
import app.domain.item.Rarity;

public record Inventory(List<Entry> items, int gold) implements OutputJsonMessage {

    public record Entry(String name, Rarity rarity, EquipmentSlot slot) {
    }

}
