package app.network.message.ingame;

import java.util.UUID;

import app.network.OutputJsonMessage;
import app.domain.item.Rarity;

public record ManaPotionUsed(UUID itemId, String name, Rarity rarity, int restoredAmount, int currentMana,
        int maxMana) implements OutputJsonMessage {

}
