package fr.idev.mudserver.network.message.ingame;

import fr.idev.mudserver.network.OutputJsonMessage;
import fr.idev.mudserver.domain.item.Rarity;

public record ManaPotionUsed(String name, Rarity rarity, int restoredAmount, int currentMana,
        int maxMana) implements OutputJsonMessage {

}
