package fr.idev.mudserver.network.message.charselect;

import java.util.List;

import fr.idev.mudserver.network.OutputJsonMessage;
import fr.idev.mudserver.domain.actor.CharacterClass;
import fr.idev.mudserver.domain.actor.Race;
import fr.idev.mudserver.network.server.telnet.Ansi;
import fr.idev.mudserver.network.server.telnet.OutputTelnetMessage;
import fr.idev.mudserver.network.server.telnet.TelnetOutput;

public record CharacterList(List<Entry> characters) implements OutputTelnetMessage, OutputJsonMessage {

    public record Entry(String name, Race race, CharacterClass characterClass, int level) {
    }

    @Override
    public void toTelnet(TelnetOutput output) {
        output.write("Your characters:\n");
        for (Entry entry : characters) {
            output.write("  " + Ansi.player(entry.name()) + " (" + entry.race() + " " + entry.characterClass()
                    + ", level " + entry.level() + ")\n");
        }
        output.write("Commands: character-select <name>, character-create <name>, character-delete <name>, logout\n");
    }
}
