package fr.idev.mudserver.network.message.lobby;

import fr.idev.mudserver.network.OutputJsonMessage;
import fr.idev.mudserver.network.server.telnet.OutputTelnetMessage;
import fr.idev.mudserver.network.server.telnet.TelnetOutput;

public record NotEnoughPlayers(int minPlayers, int currentSize) implements OutputTelnetMessage, OutputJsonMessage {

    @Override
    public void toTelnet(TelnetOutput output) {
        output.write("This world needs at least " + minPlayers + " players, your party only has " + currentSize
                + ". Type \"party-invite <login>\" to invite more.\n");
    }
}
