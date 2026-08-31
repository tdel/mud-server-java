package app.network.message.ingame;

import java.util.UUID;

import app.network.OutputJsonMessage;
import app.domain.item.ItemGrade;

public record ManaPotionUsed(UUID itemId, String name, ItemGrade grade, int restoredAmount, int currentMana,
        int maxMana) implements OutputJsonMessage {

}
