package app.network.message.ingame;

import java.util.UUID;

import app.network.OutputJsonMessage;
import app.domain.item.Rarity;

public record ItemUsed(UUID itemId, String name, Rarity rarity, int healedAmount, int currentHealth,
        int maxHealth) implements OutputJsonMessage {

}
