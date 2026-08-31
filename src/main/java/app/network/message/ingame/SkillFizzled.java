package app.network.message.ingame;

import java.util.UUID;

import app.network.OutputJsonMessage;

public record SkillFizzled(UUID skillId, String skillName, String reason) implements OutputJsonMessage {

}
