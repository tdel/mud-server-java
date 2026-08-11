package fr.idev.mudserver.network.message.lobby;

import fr.idev.mudserver.telnet.Ansi;
import fr.idev.mudserver.telnet.OutputTelnetMessage;
import fr.idev.mudserver.telnet.TelnetOutput;

public record PartyMemberLeft(String login) implements OutputTelnetMessage {

    @Override
    public void toTelnet(TelnetOutput output) {
        output.write(Ansi.player(login) + " left the party.\n");
    }
}
