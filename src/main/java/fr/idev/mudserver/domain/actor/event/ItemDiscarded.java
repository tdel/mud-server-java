package fr.idev.mudserver.domain.actor.event;

import fr.idev.mudserver.domain.actor.GamePlayer;
import fr.idev.mudserver.domain.Item;

public record ItemDiscarded(GamePlayer character, Item item) {
}
