package fr.idev.mudserver.network.message.ingame;

import fr.idev.mudserver.network.OutputJsonMessage;
import fr.idev.mudserver.network.server.telnet.Ansi;
import fr.idev.mudserver.network.server.telnet.OutputTelnetMessage;
import fr.idev.mudserver.network.server.telnet.TelnetOutput;

public record SpellLearned(String spellName) implements OutputTelnetMessage, OutputJsonMessage {

    @Override
    public void toTelnet(TelnetOutput output) {
        output.write(Ansi.success("You learn the spell " + spellName + "!") + "\n");
    }
}
