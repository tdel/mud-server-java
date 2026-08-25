package app.domain.actor.event;

import app.domain.actor.instance.CharacterInstance;
import app.domain.item.Item;

public record GamePlayerUsedManaPotion(CharacterInstance character, Item item, int restoredAmount) {
}
