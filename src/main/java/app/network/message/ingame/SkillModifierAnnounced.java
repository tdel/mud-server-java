package app.network.message.ingame;

import java.util.UUID;

import app.domain.actor.ModifiedStat;
import app.network.OutputJsonMessage;

public record SkillModifierAnnounced(UUID casterId, String casterName, UUID skillId, String skillName, UUID targetId,
        String targetName, boolean self, boolean beneficial, boolean hit, ModifiedStat stat, int amount,
        int durationSeconds, int manaSpent, int casterCurrentMana, int casterMaxMana) implements OutputJsonMessage {

}
