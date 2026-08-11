package fr.idev.mudserver.domain.actor.event;

import java.util.List;

import fr.idev.mudserver.domain.actor.GamePlayer;
import fr.idev.mudserver.domain.EquipmentSlot;
import fr.idev.mudserver.domain.Item;

public record GamePlayerEquippedItem(GamePlayer character, Item item, EquipmentSlot slot,
        List<Item> previousOccupants) {
}
