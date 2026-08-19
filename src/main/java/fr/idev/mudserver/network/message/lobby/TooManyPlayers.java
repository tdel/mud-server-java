package fr.idev.mudserver.network.message.lobby;

import fr.idev.mudserver.network.OutputJsonMessage;
import fr.idev.mudserver.network.server.telnet.OutputTelnetMessage;
import fr.idev.mudserver.network.server.telnet.TelnetOutput;

public record TooManyPlayers(int maxPlayers, int currentSize) implements OutputTelnetMessage, OutputJsonMessage {

    @Override
    public void toTelnet(TelnetOutput output) {
        output.write("This world only allows up to " + maxPlayers + " players, your party has " + currentSize
                + ". Type \"party-kick <login>\" to make room.\n");
    }
}
