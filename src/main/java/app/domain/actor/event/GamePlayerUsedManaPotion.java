package app.domain.actor.event;

import app.domain.actor.instance.PlayerInstance;
import app.domain.item.Item;

public record GamePlayerUsedManaPotion(PlayerInstance character, Item item, int restoredAmount) {
}
