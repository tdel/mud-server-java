package fr.idev.mudserver.network.message.ingame;

import fr.idev.mudserver.network.OutputJsonMessage;
import fr.idev.mudserver.domain.item.Rarity;

public record ItemUsed(String name, Rarity rarity, int healedAmount, int currentHealth,
        int maxHealth) implements OutputJsonMessage {

}
