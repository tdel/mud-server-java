package fr.idev.mudserver.network.message.ingame;

import fr.idev.mudserver.domain.actor.ModifiedStat;
import fr.idev.mudserver.network.OutputJsonMessage;

public record SpellModifierAnnounced(String casterName, String spellName, String targetName, boolean self,
        boolean beneficial, boolean hit, ModifiedStat stat, int amount,
        int durationSeconds) implements OutputJsonMessage {

}
