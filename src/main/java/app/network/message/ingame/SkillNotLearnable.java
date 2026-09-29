package app.network.message.ingame;

import java.util.UUID;

import app.network.OutputJsonMessage;

// Réponse négative à learn-skill : NOT_IN_TREE (hors de l'arbre de la classe),
// MAX_LEVEL (déjà au level maximal), LEVEL_TOO_LOW (level de personnage
// insuffisant), AUTO_LEARNED (appris d'office, pas auprès d'un maître).
public record SkillNotLearnable(UUID skillId, String skillName, String reason,
        int requiredLevel) implements OutputJsonMessage {
}
