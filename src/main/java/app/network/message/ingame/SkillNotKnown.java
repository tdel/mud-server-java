package app.network.message.ingame;

import app.network.OutputJsonMessage;

public record SkillNotKnown(String skillId) implements OutputJsonMessage {

}
