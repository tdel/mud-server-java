package fr.idev.mudserver.domain.actor.event;

import fr.idev.mudserver.domain.item.Item;
import fr.idev.mudserver.domain.actor.instance.CharacterInstance;

public record CharacterLootedItem(CharacterInstance character, Item item) {
}
