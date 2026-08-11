package fr.idev.mudserver.network.message.charselect;

import fr.idev.mudserver.telnet.OutputTelnetMessage;
import fr.idev.mudserver.telnet.TelnetOutput;

public record CharacterDeleted(String name) implements OutputTelnetMessage {

    @Override
    public void toTelnet(TelnetOutput output) {
        output.write("GamePlayer \"" + name + "\" deleted.\n");
    }
}
