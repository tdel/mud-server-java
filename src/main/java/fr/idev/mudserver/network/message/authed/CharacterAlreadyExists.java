package fr.idev.mudserver.network.message.authed;

import fr.idev.mudserver.telnet.OutputTelnetMessage;
import fr.idev.mudserver.telnet.TelnetOutput;

public record CharacterAlreadyExists(String name) implements OutputTelnetMessage {

    @Override
    public void toTelnet(TelnetOutput output) {
        output.write("You already have a character named \"" + name + "\".\n");
    }
}
