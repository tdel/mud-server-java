package fr.idev.mudserver.network.message.ingame;

import fr.idev.mudserver.network.OutputJsonMessage;
import fr.idev.mudserver.network.server.telnet.Ansi;
import fr.idev.mudserver.network.server.telnet.OutputTelnetMessage;
import fr.idev.mudserver.network.server.telnet.TelnetOutput;

public record CastResult(String spellName, String targetName, boolean selfHeal, int amount, int targetCurrentHealth,
        int targetMaxHealth, boolean targetDefeated) implements OutputTelnetMessage, OutputJsonMessage {

    @Override
    public void toTelnet(TelnetOutput output) {
        if (selfHeal) {
            output.write("You cast " + spellName + " and recover " + Ansi.heal(amount) + " HP (" + targetCurrentHealth
                    + "/" + targetMaxHealth + ").\n");
            return;
        }

        output.write("You cast " + spellName + " on " + targetName + " for " + Ansi.damage(amount) + " damage. ("
                + targetCurrentHealth + "/" + targetMaxHealth + " HP)\n");

        if (targetDefeated) {
            output.write(Ansi.success(targetName + " is defeated!") + "\n");
        }
    }
}
