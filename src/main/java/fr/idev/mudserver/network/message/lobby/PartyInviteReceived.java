package fr.idev.mudserver.network.message.lobby;

import fr.idev.mudserver.network.OutputJsonMessage;
import fr.idev.mudserver.network.server.telnet.Ansi;
import fr.idev.mudserver.network.server.telnet.OutputTelnetMessage;
import fr.idev.mudserver.network.server.telnet.TelnetOutput;

public record PartyInviteReceived(String leaderLogin) implements OutputTelnetMessage, OutputJsonMessage {

    @Override
    public void toTelnet(TelnetOutput output) {
        output.write(Ansi.player(leaderLogin) + " invited you to their party. Type \"party-accept\" to join.\n");
    }
}
