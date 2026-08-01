package fr.idev.mudserver.network.message.connected;

import fr.idev.mudserver.telnet.OutputTelnetMessage;
import fr.idev.mudserver.telnet.TelnetOutput;

public record AccountCreated(String login) implements OutputTelnetMessage {

    @Override
    public void toTelnet(TelnetOutput output) {
        output.write("Account \"" + login + "\" created.\n\n");
    }
}
