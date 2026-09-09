package app.domain.actor.event;

import app.domain.actor.instance.PlayerInstance;
import app.domain.item.ItemGrade;
import app.domain.item.ItemType;

// newGrade == null : le joueur vient de désactiver l'auto-use pour cette catégorie.
public record ShotGradeToggled(PlayerInstance character, ItemType shotType, ItemGrade newGrade) {
}
