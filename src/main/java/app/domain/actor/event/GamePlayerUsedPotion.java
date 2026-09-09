package app.domain.actor.event;

import app.domain.item.Item;
import app.domain.actor.instance.PlayerInstance;

public record GamePlayerUsedPotion(PlayerInstance character, Item item, int healedAmount) {
}
