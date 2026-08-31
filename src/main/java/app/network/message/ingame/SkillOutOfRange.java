package app.network.message.ingame;

import app.network.OutputJsonMessage;

public record SkillOutOfRange(String skillName, String targetName) implements OutputJsonMessage {

}
