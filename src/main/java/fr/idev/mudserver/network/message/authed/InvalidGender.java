package fr.idev.mudserver.network.message.authed;

import fr.idev.mudserver.telnet.OutputTelnetMessage;
import fr.idev.mudserver.telnet.TelnetOutput;

public record InvalidGender(String input) implements OutputTelnetMessage {

    @Override
    public void toTelnet(TelnetOutput output) {
        output.write("\"" + input + "\" is not a valid gender.\n");
    }
}
