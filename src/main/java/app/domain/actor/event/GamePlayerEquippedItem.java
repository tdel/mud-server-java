package app.domain.actor.event;

import java.util.List;

import app.domain.actor.instance.CharacterInstance;
import app.domain.item.EquipmentSlot;
import app.domain.item.Item;

public record GamePlayerEquippedItem(CharacterInstance character, Item item, EquipmentSlot slot,
        List<Item> previousOccupants) {
}
