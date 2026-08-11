package fr.idev.mudserver.network.message.charselect;

import fr.idev.mudserver.telnet.OutputTelnetMessage;
import fr.idev.mudserver.telnet.TelnetOutput;

public record InvalidRace(String input) implements OutputTelnetMessage {

    @Override
    public void toTelnet(TelnetOutput output) {
        output.write("\"" + input + "\" is not a valid race.\n");
    }
}
