package app.network.message.ingame;

import java.util.UUID;

import app.network.OutputJsonMessage;

public record SkillCastAnnounced(UUID casterId, String casterName, UUID skillId, String skillName, UUID targetId,
        String targetName, boolean selfHeal, boolean hit, int amount, int targetHealthAfter, int targetMaxHealth,
        boolean targetDefeated) implements OutputJsonMessage {

}
