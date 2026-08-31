package app.network.message.ingame;

import java.util.UUID;

import app.network.OutputJsonMessage;

public record SkillCastCancelled(UUID casterId, String casterName, UUID skillId,
        String skillName) implements OutputJsonMessage {

}
