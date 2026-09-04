package app.network.message.ingame;

import app.domain.item.ItemGrade;
import app.domain.item.ItemType;
import app.network.OutputJsonMessage;

// grade == null : l'auto-use vient d'être désactivé pour cette catégorie.
public record ShotGradeChanged(ItemType shotType, ItemGrade grade) implements OutputJsonMessage {

}
