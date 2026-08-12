package fr.idev.mudserver.network.message;

import java.util.List;

import fr.idev.mudserver.telnet.OutputTelnetMessage;
import fr.idev.mudserver.telnet.TelnetOutput;

public record Help(List<String> commands) implements OutputTelnetMessage {

    @Override
    public void toTelnet(TelnetOutput output) {
        output.write("Available commands: " + String.join(", ", commands) + "\n");
    }
}
