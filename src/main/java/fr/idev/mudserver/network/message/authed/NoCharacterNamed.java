package fr.idev.mudserver.network.message.authed;

import fr.idev.mudserver.telnet.OutputTelnetMessage;
import fr.idev.mudserver.telnet.TelnetOutput;

public record NoCharacterNamed(String name) implements OutputTelnetMessage {

    @Override
    public void toTelnet(TelnetOutput output) {
        output.write("No character named \"" + name + "\".\n");
    }
}
