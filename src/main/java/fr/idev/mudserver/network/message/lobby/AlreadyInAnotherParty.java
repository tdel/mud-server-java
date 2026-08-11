package fr.idev.mudserver.network.message.lobby;

import fr.idev.mudserver.telnet.OutputTelnetMessage;
import fr.idev.mudserver.telnet.TelnetOutput;

public record AlreadyInAnotherParty(String login) implements OutputTelnetMessage {

    @Override
    public void toTelnet(TelnetOutput output) {
        output.write(login + " is already in a party.\n");
    }
}
