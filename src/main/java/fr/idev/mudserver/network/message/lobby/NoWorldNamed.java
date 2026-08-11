package fr.idev.mudserver.network.message.lobby;

import fr.idev.mudserver.telnet.OutputTelnetMessage;
import fr.idev.mudserver.telnet.TelnetOutput;

public record NoWorldNamed(String shortName) implements OutputTelnetMessage {

    @Override
    public void toTelnet(TelnetOutput output) {
        output.write("No world named \"" + shortName + "\".\n");
    }
}
