package fr.idev.mudserver.network.message.authed;

import java.util.List;

import fr.idev.mudserver.telnet.OutputTelnetMessage;
import fr.idev.mudserver.telnet.TelnetOutput;

public record CharacterList(List<String> names) implements OutputTelnetMessage {

    @Override
    public void toTelnet(TelnetOutput output) {
        output.write(names.isEmpty()
                ? "You have no characters yet. Use \"character-create <name>\" to make one.\n"
                : "Characters: " + String.join(", ", names) + "\n");
        output.write("Commands: character-select <name>, character-create <name>, character-delete <name>, logout\n");
    }
}
