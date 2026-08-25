package app.domain.actor.event;

import app.domain.actor.instance.CharacterInstance;
import app.domain.item.Item;

public record GamePlayerUnequippedItem(CharacterInstance character, Item item) {
}
