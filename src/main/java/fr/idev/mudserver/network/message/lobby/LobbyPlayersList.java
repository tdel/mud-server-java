package fr.idev.mudserver.network.message.lobby;

import java.util.List;

import fr.idev.mudserver.network.OutputJsonMessage;
import fr.idev.mudserver.network.server.telnet.OutputTelnetMessage;
import fr.idev.mudserver.network.server.telnet.TelnetOutput;

public record LobbyPlayersList(List<String> logins) implements OutputTelnetMessage, OutputJsonMessage {

    @Override
    public void toTelnet(TelnetOutput output) {
        if (logins.isEmpty()) {
            output.write("No one else in the lobby.\n");
            return;
        }
        output.write("In the lobby: " + String.join(", ", logins) + "\n");
    }
}
