package fr.idev.mudserver.network.message.lobby;

import fr.idev.mudserver.telnet.OutputTelnetMessage;
import fr.idev.mudserver.telnet.TelnetOutput;

public record PlayerNotOnline(String login) implements OutputTelnetMessage {

    @Override
    public void toTelnet(TelnetOutput output) {
        output.write(login + " is not online in the lobby.\n");
    }
}
