package fr.idev.mudserver.network.message.lobby;

import fr.idev.mudserver.telnet.OutputTelnetMessage;
import fr.idev.mudserver.telnet.TelnetOutput;

public record NotPartyLeader() implements OutputTelnetMessage {

    @Override
    public void toTelnet(TelnetOutput output) {
        output.write("Only the party leader can do that.\n");
    }
}
