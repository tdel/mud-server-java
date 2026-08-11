package fr.idev.mudserver.domain.actor.event;

import fr.idev.mudserver.domain.Item;
import fr.idev.mudserver.domain.actor.GamePlayer;

public record GamePlayerUsedPotion(GamePlayer character, Item item, int healedAmount) {
}
