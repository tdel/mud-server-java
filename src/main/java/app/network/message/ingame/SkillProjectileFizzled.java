package app.network.message.ingame;

import java.util.UUID;

import app.network.OutputJsonMessage;

public record SkillProjectileFizzled(UUID projectileId, UUID skillId, String skillName) implements OutputJsonMessage {

}
