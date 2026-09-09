package app.domain.actor.event;

import app.domain.actor.instance.PlayerInstance;
import app.domain.item.Item;

public record ItemDiscarded(PlayerInstance character, Item item) {
}
