package fr.idev.mudserver.network.message.charselect;

import fr.idev.mudserver.telnet.OutputTelnetMessage;
import fr.idev.mudserver.telnet.TelnetOutput;

public record NoCharacterInWorld(String worldName) implements OutputTelnetMessage {

    @Override
    public void toTelnet(TelnetOutput output) {
        output.write("You have no character in " + worldName + " yet.\n");
        output.write("Commands: character-create <name>, logout\n");
    }
}
