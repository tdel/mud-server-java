package fr.idev.mudserver.network.message.lobby;

import fr.idev.mudserver.telnet.Ansi;
import fr.idev.mudserver.telnet.OutputTelnetMessage;
import fr.idev.mudserver.telnet.TelnetOutput;

public record PartyMemberJoined(String login) implements OutputTelnetMessage {

    @Override
    public void toTelnet(TelnetOutput output) {
        output.write(Ansi.player(login) + " joined the party.\n");
    }
}
