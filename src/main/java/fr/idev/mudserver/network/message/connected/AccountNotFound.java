package fr.idev.mudserver.network.message.connected;

import fr.idev.mudserver.telnet.OutputTelnetMessage;
import fr.idev.mudserver.telnet.TelnetOutput;

public record AccountNotFound(String login) implements OutputTelnetMessage {

    @Override
    public void toTelnet(TelnetOutput output) {
        output.write("No account found for \"" + login + "\". Use \"register " + login + "\" to create one.\n");
    }
}
