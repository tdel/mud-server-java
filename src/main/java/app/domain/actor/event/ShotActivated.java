package app.domain.actor.event;

import app.domain.actor.instance.PlayerInstance;
import app.domain.item.Item;
import app.domain.item.ItemGrade;
import app.domain.item.ItemType;

public record ShotActivated(PlayerInstance character, Item item, ItemType shotType, ItemGrade grade,
        int remainingQuantity) {
}
