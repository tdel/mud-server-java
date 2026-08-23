package fr.idev.mudserver.network.message.ingame;

import fr.idev.mudserver.domain.actor.ModifiedStat;
import fr.idev.mudserver.network.OutputJsonMessage;
import fr.idev.mudserver.network.server.telnet.Ansi;
import fr.idev.mudserver.network.server.telnet.OutputTelnetMessage;
import fr.idev.mudserver.network.server.telnet.TelnetOutput;

public record SpellModifierAnnounced(String casterName, String spellName, String targetName, boolean self,
        boolean beneficial, boolean hit, ModifiedStat stat, int amount,
        int durationSeconds) implements OutputTelnetMessage, OutputJsonMessage {

    @Override
    public void toTelnet(TelnetOutput output) {
        String targetLabel = self ? "themselves" : targetName;

        if (!hit) {
            output.write(casterName + " casts " + spellName + " on " + targetLabel + " but the spell misses!\n");
            return;
        }

        String signedAmount = amount >= 0 ? "+" + amount : String.valueOf(amount);
        String coloredAmount = beneficial ? Ansi.success(signedAmount) : Ansi.error(signedAmount);

        output.write(casterName + " casts " + spellName + " on " + targetLabel + ": " + coloredAmount + " to "
                + stat.label() + " for " + durationSeconds + "s.\n");
    }
}
