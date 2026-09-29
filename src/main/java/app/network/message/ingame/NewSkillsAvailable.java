package app.network.message.ingame;

import app.network.OutputJsonMessage;

// Envoyé après une montée de level : `count` compétences (levels) sont
// désormais apprenables auprès d'un PNJ SKILL_LEARNER.
public record NewSkillsAvailable(int level, int count) implements OutputJsonMessage {
}
