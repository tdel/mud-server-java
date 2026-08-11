package fr.idev.mudserver.network.message.lobby;

import fr.idev.mudserver.telnet.Ansi;
import fr.idev.mudserver.telnet.OutputTelnetMessage;
import fr.idev.mudserver.telnet.TelnetOutput;

public record PartyInviteReceived(String leaderLogin) implements OutputTelnetMessage {

    @Override
    public void toTelnet(TelnetOutput output) {
        output.write(Ansi.player(leaderLogin) + " invited you to their party. Type \"party-accept\" to join.\n");
    }
}
