package app.network.message.ingame;

import java.util.List;
import java.util.UUID;

import app.domain.StatModifier;
import app.network.OutputJsonMessage;

public record SkillModifierAnnounced(UUID casterId, String casterName, UUID skillId, String skillName, UUID targetId,
        String targetName, boolean self, boolean beneficial, boolean hit, List<StatModifier> modifiers, int amount,
        int durationSeconds, int manaSpent, int casterCurrentMana, int casterMaxMana) implements OutputJsonMessage {

}
