package app.domain.actor.event;

import java.util.List;

import app.domain.actor.instance.PlayerInstance;
import app.domain.item.EquipmentSlot;
import app.domain.item.Item;

public record GamePlayerEquippedItem(PlayerInstance character, Item item, EquipmentSlot slot,
        List<Item> previousOccupants) {
}
