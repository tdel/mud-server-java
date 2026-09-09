package app.domain.actor.event;

import app.domain.actor.instance.PlayerInstance;
import app.domain.item.ItemGrade;
import app.domain.item.ItemType;

public record ShotGradeDepleted(PlayerInstance character, ItemType shotType, ItemGrade grade) {
}
