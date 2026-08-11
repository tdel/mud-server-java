package fr.idev.mudserver.network.message.lobby;

import fr.idev.mudserver.telnet.Ansi;
import fr.idev.mudserver.telnet.OutputTelnetMessage;
import fr.idev.mudserver.telnet.TelnetOutput;

public record PartyInviteSent(String targetLogin) implements OutputTelnetMessage {

    @Override
    public void toTelnet(TelnetOutput output) {
        output.write("Invite sent to " + Ansi.player(targetLogin) + ".\n");
    }
}
