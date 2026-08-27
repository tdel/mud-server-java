package app.network.message.ingame;

import java.util.UUID;

import app.network.OutputJsonMessage;

public record SpellCastObserved(UUID casterId, String casterName, UUID spellId, String spellName, UUID targetId,
        String targetName, boolean selfHeal, boolean hit, int amount,
        boolean targetDefeated) implements OutputJsonMessage {

}
