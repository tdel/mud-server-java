package app.domain.actor.event;

import app.domain.item.Item;
import app.domain.actor.instance.CharacterInstance;

public record CharacterLootedItem(CharacterInstance character, Item item) {
}
