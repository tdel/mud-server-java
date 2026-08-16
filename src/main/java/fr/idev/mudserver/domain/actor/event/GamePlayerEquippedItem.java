package fr.idev.mudserver.domain.actor.event;

import java.util.List;

import fr.idev.mudserver.domain.actor.instance.CharacterInstance;
import fr.idev.mudserver.domain.item.EquipmentSlot;
import fr.idev.mudserver.domain.item.Item;

public record GamePlayerEquippedItem(CharacterInstance character, Item item, EquipmentSlot slot,
        List<Item> previousOccupants) {
}
