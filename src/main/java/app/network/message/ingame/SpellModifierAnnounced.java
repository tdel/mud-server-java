package app.network.message.ingame;

import java.util.UUID;

import app.domain.actor.ModifiedStat;
import app.network.OutputJsonMessage;

public record SpellModifierAnnounced(UUID casterId, String casterName, UUID spellId, String spellName, UUID targetId,
        String targetName, boolean self, boolean beneficial, boolean hit, ModifiedStat stat, int amount,
        int durationSeconds, int manaSpent, int casterCurrentMana, int casterMaxMana) implements OutputJsonMessage {

}
