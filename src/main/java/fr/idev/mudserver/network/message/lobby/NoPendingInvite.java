package fr.idev.mudserver.network.message.lobby;

import fr.idev.mudserver.telnet.OutputTelnetMessage;
import fr.idev.mudserver.telnet.TelnetOutput;

public record NoPendingInvite() implements OutputTelnetMessage {

    @Override
    public void toTelnet(TelnetOutput output) {
        output.write("You have no pending party invite.\n");
    }
}
