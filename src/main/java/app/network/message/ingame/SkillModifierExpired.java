package app.network.message.ingame;

import java.util.UUID;

import app.network.OutputJsonMessage;

// characterId : plusieurs monstres portent le même nom, seul l'UUID dit lequel
// a perdu l'effet.
public record SkillModifierExpired(UUID characterId, String characterName,
        String skillName) implements OutputJsonMessage {

}
