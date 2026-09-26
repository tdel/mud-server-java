package app.network.message.ingame;

import java.util.UUID;

import app.network.OutputJsonMessage;

public record SkillCastStarted(UUID casterId, String casterName, UUID skillId, String skillName, UUID targetId,
        String targetName, int castingTimeMs, double casterHeading) implements OutputJsonMessage {

}
