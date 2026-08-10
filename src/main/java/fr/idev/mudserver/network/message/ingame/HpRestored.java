package fr.idev.mudserver.network.message.ingame;

import fr.idev.mudserver.telnet.Ansi;
import fr.idev.mudserver.telnet.OutputTelnetMessage;
import fr.idev.mudserver.telnet.TelnetOutput;

/** Message privé envoyé à chaque joueur affecté par un repos court ou long. */
public record HpRestored(int healedAmount, int currentHealth, int maxHealth) implements OutputTelnetMessage {

    @Override
    public void toTelnet(TelnetOutput output) {
        output.write("You recover " + Ansi.heal(healedAmount) + " HP (" + currentHealth + "/" + maxHealth + ").\n");
    }
}
