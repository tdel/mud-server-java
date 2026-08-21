package fr.idev.mudserver.network.message.ingame;

import fr.idev.mudserver.network.OutputJsonMessage;
import fr.idev.mudserver.network.server.telnet.Ansi;
import fr.idev.mudserver.network.server.telnet.OutputTelnetMessage;
import fr.idev.mudserver.network.server.telnet.TelnetOutput;

public record AttackReceived(String attackerName, boolean hit, boolean critical, int damage, int currentHealth,
        int maxHealth, boolean defeated) implements OutputTelnetMessage, OutputJsonMessage {

    @Override
    public void toTelnet(TelnetOutput output) {
        if (!hit) {
            output.write(attackerName + " misses you.\n");
            return;
        }

        String criticalLabel = critical ? Ansi.critical(" (critical hit)") : "";
        output.write(attackerName + " hits you" + criticalLabel + " for " + Ansi.damage(damage) + " damage. ("
                + currentHealth + "/" + maxHealth + " HP)\n");

        if (defeated) {
            output.write(Ansi.error("You are defeated!") + "\n");
        }
    }
}
