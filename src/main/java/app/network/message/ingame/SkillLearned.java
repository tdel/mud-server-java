package app.network.message.ingame;

import app.network.OutputJsonMessage;

public record SkillLearned(String skillName, int tier, boolean upgraded) implements OutputJsonMessage {

}
