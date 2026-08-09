package fr.idev.mudserver.network.message.ingame;

import fr.idev.mudserver.telnet.Ansi;
import fr.idev.mudserver.telnet.OutputTelnetMessage;
import fr.idev.mudserver.telnet.TelnetOutput;

public record ItemNotUsable(String name) implements OutputTelnetMessage {

    @Override
    public void toTelnet(TelnetOutput output) {
        output.write(Ansi.error("The " + name + " can't be used.") + "\n");
    }
}
