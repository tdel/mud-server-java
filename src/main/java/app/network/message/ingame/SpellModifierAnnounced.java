package app.network.message.ingame;

import app.domain.actor.ModifiedStat;
import app.network.OutputJsonMessage;

public record SpellModifierAnnounced(String casterName, String spellName, String targetName, boolean self,
        boolean beneficial, boolean hit, ModifiedStat stat, int amount,
        int durationSeconds) implements OutputJsonMessage {

}
