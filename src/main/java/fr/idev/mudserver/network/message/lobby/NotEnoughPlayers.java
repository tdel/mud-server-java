package fr.idev.mudserver.network.message.lobby;

import fr.idev.mudserver.telnet.OutputTelnetMessage;
import fr.idev.mudserver.telnet.TelnetOutput;

public record NotEnoughPlayers(int minPlayers, int currentSize) implements OutputTelnetMessage {

    @Override
    public void toTelnet(TelnetOutput output) {
        output.write("This world needs at least " + minPlayers + " players, your party only has " + currentSize
                + ". Type \"party-invite <login>\" to invite more.\n");
    }
}
