package app.network.message.ingame;

import java.util.UUID;

import app.network.OutputJsonMessage;

// Un prêtre propose de ramener le joueur mort à la vie (Resurrection) : le
// client demande confirmation, puis envoie resurrect-accept (ou ignore).
public record ResurrectionOffered(UUID casterId, String casterName, String skillName,
        int expiresInSeconds) implements OutputJsonMessage {
}
