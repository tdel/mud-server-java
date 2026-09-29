package app.network.message.ingame;

import java.util.UUID;

import app.domain.item.ItemGrade;
import app.network.OutputJsonMessage;

/**
 * Parchemin lu (consommé) : son sort démarre juste après (SkillCastStarted).
 */
public record ScrollUsed(UUID itemId, String name, ItemGrade grade, String skillName,
        int remainingQuantity) implements OutputJsonMessage {

}
