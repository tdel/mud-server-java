package fr.idev.mudserver.network.message.connected;

import fr.idev.mudserver.telnet.OutputTelnetMessage;
import fr.idev.mudserver.telnet.TelnetOutput;

public record LoginAlreadyTaken(String login) implements OutputTelnetMessage {

    @Override
    public void toTelnet(TelnetOutput output) {
        output.write("The login \"" + login + "\" is already taken.\n");
    }
}
