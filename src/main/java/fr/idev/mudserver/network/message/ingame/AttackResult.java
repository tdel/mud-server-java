package fr.idev.mudserver.network.message.ingame;

import fr.idev.mudserver.network.OutputJsonMessage;
import fr.idev.mudserver.network.server.telnet.Ansi;
import fr.idev.mudserver.network.server.telnet.OutputTelnetMessage;
import fr.idev.mudserver.network.server.telnet.TelnetOutput;

public record AttackResult(String targetName, boolean hit, boolean critical, int damage, int targetCurrentHealth,
        int targetMaxHealth, boolean targetDefeated) implements OutputTelnetMessage, OutputJsonMessage {

    @Override
    public void toTelnet(TelnetOutput output) {
        if (!hit) {
            output.write("You miss " + targetName + ".\n");
            return;
        }

        String criticalLabel = critical ? Ansi.critical(" (critical hit)") : "";
        output.write("You hit " + targetName + criticalLabel + " for " + Ansi.damage(damage) + " damage. ("
                + targetCurrentHealth + "/" + targetMaxHealth + " HP)\n");

        if (targetDefeated) {
            output.write(Ansi.success(targetName + " is defeated!") + "\n");
        }
    }
}
