package app.network.message.ingame;

import java.util.UUID;

import app.network.OutputJsonMessage;

/**
 * {@code beneficial} : buff (vrai) ou debuff (faux), même règle que
 * ActiveEffect.category() — la fenêtre de groupe range les deux sur des lignes
 * distinctes.
 */
public record PartyMemberEffectApplied(UUID characterId, String characterName, String skillName, String stat,
        int amount, long secondsRemaining, boolean beneficial) implements OutputJsonMessage {

}
