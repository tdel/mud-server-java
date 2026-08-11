package fr.idev.mudserver.network.message.lobby;

import fr.idev.mudserver.telnet.OutputTelnetMessage;
import fr.idev.mudserver.telnet.TelnetOutput;

public record TooManyPlayers(int maxPlayers, int currentSize) implements OutputTelnetMessage {

    @Override
    public void toTelnet(TelnetOutput output) {
        output.write("This world only allows up to " + maxPlayers + " players, your party has " + currentSize
                + ". Type \"party-kick <login>\" to make room.\n");
    }
}
