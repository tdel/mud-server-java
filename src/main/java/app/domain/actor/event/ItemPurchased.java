package app.domain.actor.event;

import app.domain.item.Item;
import app.domain.actor.instance.CharacterInstance;

public record ItemPurchased(CharacterInstance character, Item item, int price) {
}
