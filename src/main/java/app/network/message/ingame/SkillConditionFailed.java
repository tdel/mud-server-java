package app.network.message.ingame;

import app.network.OutputJsonMessage;

// Condition propre à un sort non remplie (bouclier, dos de la cible,
// serviteur, cadavre, silence...), reason affichable.
public record SkillConditionFailed(String skillName, String reason) implements OutputJsonMessage {
}
