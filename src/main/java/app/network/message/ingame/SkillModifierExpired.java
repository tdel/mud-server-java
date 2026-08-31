package app.network.message.ingame;

import app.network.OutputJsonMessage;

public record SkillModifierExpired(String characterName, String skillName) implements OutputJsonMessage {

}
