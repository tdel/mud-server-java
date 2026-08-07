package fr.idev.mudserver.network.message.ingame;

import fr.idev.mudserver.telnet.OutputTelnetMessage;
import fr.idev.mudserver.telnet.TelnetOutput;

public record InvalidDialogueChoice(String input) implements OutputTelnetMessage {

    @Override
    public void toTelnet(TelnetOutput output) {
        output.write("\"" + input + "\" is not a valid choice.\n");
    }
}
