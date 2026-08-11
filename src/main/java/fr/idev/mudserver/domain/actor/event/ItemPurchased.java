package fr.idev.mudserver.domain.actor.event;

import fr.idev.mudserver.domain.Item;
import fr.idev.mudserver.domain.actor.GamePlayer;

public record ItemPurchased(GamePlayer character, Item item, int price) {
}
