package app.domain.actor.event;

import app.domain.actor.instance.PlayerInstance;
import app.domain.item.Item;

public record GamePlayerUnequippedItem(PlayerInstance character, Item item) {
}
