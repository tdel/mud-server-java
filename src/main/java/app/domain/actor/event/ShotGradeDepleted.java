package app.domain.actor.event;

import app.domain.actor.instance.CharacterInstance;
import app.domain.item.ItemGrade;
import app.domain.item.ItemType;

public record ShotGradeDepleted(CharacterInstance character, ItemType shotType, ItemGrade grade) {
}
