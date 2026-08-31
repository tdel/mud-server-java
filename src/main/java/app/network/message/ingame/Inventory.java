package app.network.message.ingame;

import java.util.List;
import java.util.UUID;

import app.network.OutputJsonMessage;
import app.domain.item.EquipmentSlot;
import app.domain.item.ItemGrade;

public record Inventory(List<Entry> items, int gold) implements OutputJsonMessage {

    public record Entry(UUID id, String name, ItemGrade grade, EquipmentSlot slot) {
    }

}
