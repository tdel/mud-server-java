package fr.idev.mudserver.network.message.lobby;

import fr.idev.mudserver.network.OutputJsonMessage;
import fr.idev.mudserver.network.server.telnet.OutputTelnetMessage;
import fr.idev.mudserver.network.server.telnet.TelnetOutput;

public record CannotKickSelf() implements OutputTelnetMessage, OutputJsonMessage {

    @Override
    public void toTelnet(TelnetOutput output) {
        output.write("You cannot kick yourself. Type \"party-leave\" instead.\n");
    }
}
