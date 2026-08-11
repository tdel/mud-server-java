package fr.idev.mudserver.network.message.lobby;

import fr.idev.mudserver.telnet.OutputTelnetMessage;
import fr.idev.mudserver.telnet.TelnetOutput;

public record CannotInviteSelf() implements OutputTelnetMessage {

    @Override
    public void toTelnet(TelnetOutput output) {
        output.write("You cannot invite yourself.\n");
    }
}
