package fr.idev.mudserver.network.message.ingame;

import fr.idev.mudserver.network.OutputJsonMessage;
import fr.idev.mudserver.network.server.telnet.Ansi;
import fr.idev.mudserver.network.server.telnet.OutputTelnetMessage;
import fr.idev.mudserver.network.server.telnet.TelnetOutput;

public record RegenTick(int hpRestored, int manaRestored, int currentHealth, int maxHealth, int currentMana,
        int maxMana) implements OutputTelnetMessage, OutputJsonMessage {

    @Override
    public void toTelnet(TelnetOutput output) {
        output.write("You recover " + Ansi.heal(hpRestored) + " HP (" + currentHealth + "/" + maxHealth + ") and "
                + manaRestored + " mana (" + currentMana + "/" + maxMana + ").\n");
    }
}
