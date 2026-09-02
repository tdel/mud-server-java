package app.network.message.ingame;

import app.network.OutputJsonMessage;

public record SkillLearned(String skillName, int level, boolean upgraded) implements OutputJsonMessage {

}
