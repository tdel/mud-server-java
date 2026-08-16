package fr.idev.mudserver.domain.actor.event;

import fr.idev.mudserver.domain.actor.instance.CharacterInstance;
import fr.idev.mudserver.domain.item.Item;

public record ItemDiscarded(CharacterInstance character, Item item) {
}
