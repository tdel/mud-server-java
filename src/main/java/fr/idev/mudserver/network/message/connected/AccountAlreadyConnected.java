package fr.idev.mudserver.network.message.connected;

import fr.idev.mudserver.telnet.OutputTelnetMessage;
import fr.idev.mudserver.telnet.TelnetOutput;

public record AccountAlreadyConnected(String login) implements OutputTelnetMessage {

    @Override
    public void toTelnet(TelnetOutput output) {
        output.write("The account \"" + login + "\" is already connected.\n");
    }
}
