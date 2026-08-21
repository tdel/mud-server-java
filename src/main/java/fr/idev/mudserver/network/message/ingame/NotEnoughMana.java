package fr.idev.mudserver.network.message.ingame;

import fr.idev.mudserver.network.OutputJsonMessage;
import fr.idev.mudserver.network.server.telnet.Ansi;
import fr.idev.mudserver.network.server.telnet.OutputTelnetMessage;
import fr.idev.mudserver.network.server.telnet.TelnetOutput;

public record NotEnoughMana(String spellName, int manaCost,
        int currentMana) implements OutputTelnetMessage, OutputJsonMessage {

    @Override
    public void toTelnet(TelnetOutput output) {
        output.write(Ansi
                .error("Not enough mana to cast " + spellName + " (" + manaCost + " needed, " + currentMana + " left).")
                + "\n");
    }
}
