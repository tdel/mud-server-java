package app.network.message.ingame;

import app.network.OutputJsonMessage;
import app.domain.item.Rarity;

public record ManaPotionUsed(String name, Rarity rarity, int restoredAmount, int currentMana,
        int maxMana) implements OutputJsonMessage {

}
