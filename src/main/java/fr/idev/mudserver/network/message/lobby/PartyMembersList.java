package fr.idev.mudserver.network.message.lobby;

import java.util.List;
import java.util.stream.Collectors;

import fr.idev.mudserver.network.OutputJsonMessage;
import fr.idev.mudserver.network.server.telnet.Ansi;
import fr.idev.mudserver.network.server.telnet.OutputTelnetMessage;
import fr.idev.mudserver.network.server.telnet.TelnetOutput;

public record PartyMembersList(List<String> logins) implements OutputTelnetMessage, OutputJsonMessage {

    @Override
    public void toTelnet(TelnetOutput output) {
        if (logins.isEmpty()) {
            output.write("Your party has no members.\n");
            return;
        }
        String rendered = logins.stream().map(Ansi::player).collect(Collectors.joining(", "));
        output.write("Party members: " + rendered + "\n");
    }
}
