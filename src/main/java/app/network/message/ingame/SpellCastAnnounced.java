package app.network.message.ingame;

import java.util.UUID;

import app.network.OutputJsonMessage;

public record SpellCastAnnounced(UUID casterId, String casterName, UUID spellId, String spellName, UUID targetId,
        String targetName, boolean selfHeal, boolean hit, int amount, int targetHealthAfter, int targetMaxHealth,
        boolean targetDefeated) implements OutputJsonMessage {

}
