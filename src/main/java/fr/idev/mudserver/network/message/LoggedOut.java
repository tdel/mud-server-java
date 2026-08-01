package fr.idev.mudserver.network.message;

import fr.idev.mudserver.telnet.OutputTelnetMessage;
import fr.idev.mudserver.telnet.TelnetOutput;

public record LoggedOut() implements OutputTelnetMessage {

    @Override
    public void toTelnet(TelnetOutput output) {
        output.write("You have logged out.\nType \"login <name>\" or \"register <name>\" to begin.\n");
    }
}
