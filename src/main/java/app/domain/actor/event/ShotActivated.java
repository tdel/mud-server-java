package app.domain.actor.event;

import app.domain.actor.instance.CharacterInstance;
import app.domain.item.Item;
import app.domain.item.ItemGrade;
import app.domain.item.ItemType;

public record ShotActivated(CharacterInstance character, Item item, ItemType shotType, ItemGrade grade,
        int remainingQuantity) {
}
