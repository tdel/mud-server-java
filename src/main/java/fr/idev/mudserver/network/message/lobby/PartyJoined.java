package fr.idev.mudserver.network.message.lobby;

import fr.idev.mudserver.telnet.Ansi;
import fr.idev.mudserver.telnet.OutputTelnetMessage;
import fr.idev.mudserver.telnet.TelnetOutput;

public record PartyJoined(String leaderLogin, int size) implements OutputTelnetMessage {

    @Override
    public void toTelnet(TelnetOutput output) {
        output.write("You joined " + Ansi.player(leaderLogin) + "'s party (" + size + " members).\n");
    }
}
