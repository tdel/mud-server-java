package app.network.message.ingame;

import java.util.UUID;

import app.network.OutputJsonMessage;
import app.domain.item.ItemGrade;

public record ItemUsed(UUID itemId, String name, ItemGrade grade, int healedAmount, int currentHealth,
        int maxHealth) implements OutputJsonMessage {

}
