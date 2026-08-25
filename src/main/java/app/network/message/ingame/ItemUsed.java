package app.network.message.ingame;

import app.network.OutputJsonMessage;
import app.domain.item.Rarity;

public record ItemUsed(String name, Rarity rarity, int healedAmount, int currentHealth,
        int maxHealth) implements OutputJsonMessage {

}
