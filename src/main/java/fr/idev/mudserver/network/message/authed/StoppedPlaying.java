package fr.idev.mudserver.network.message.authed;

import fr.idev.mudserver.telnet.OutputTelnetMessage;
import fr.idev.mudserver.telnet.TelnetOutput;

public record StoppedPlaying(String characterName) implements OutputTelnetMessage {

    @Override
    public void toTelnet(TelnetOutput output) {
        output.write("You stop playing " + characterName + ".\n\n");
    }
}
