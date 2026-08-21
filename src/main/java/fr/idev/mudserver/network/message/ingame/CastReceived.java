package fr.idev.mudserver.network.message.ingame;

import fr.idev.mudserver.network.OutputJsonMessage;
import fr.idev.mudserver.network.server.telnet.Ansi;
import fr.idev.mudserver.network.server.telnet.OutputTelnetMessage;
import fr.idev.mudserver.network.server.telnet.TelnetOutput;

public record CastReceived(String casterName, String spellName, int amount, int currentHealth, int maxHealth,
        boolean defeated) implements OutputTelnetMessage, OutputJsonMessage {

    @Override
    public void toTelnet(TelnetOutput output) {
        output.write(casterName + " casts " + spellName + " on you for " + Ansi.damage(amount) + " damage. ("
                + currentHealth + "/" + maxHealth + " HP)\n");

        if (defeated) {
            output.write(Ansi.error("You are defeated!") + "\n");
        }
    }
}
