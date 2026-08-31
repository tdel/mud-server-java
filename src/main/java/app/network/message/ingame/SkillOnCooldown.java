package app.network.message.ingame;

import app.network.OutputJsonMessage;

public record SkillOnCooldown(String skillName, long remainingMillis) implements OutputJsonMessage {

}
