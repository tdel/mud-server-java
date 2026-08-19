package fr.idev.mudserver.network.message.charselect;

import fr.idev.mudserver.network.OutputJsonMessage;
import fr.idev.mudserver.domain.actor.CharacterClass;
import fr.idev.mudserver.network.server.telnet.Ansi;
import fr.idev.mudserver.network.server.telnet.OutputTelnetMessage;
import fr.idev.mudserver.network.server.telnet.TelnetOutput;

public record ExistingCharacterInWorld(String worldName, String characterName, CharacterClass characterClass,
        int level) implements OutputTelnetMessage, OutputJsonMessage {

    @Override
    public void toTelnet(TelnetOutput output) {
        output.write("You already have a character in " + worldName + ": " + Ansi.player(characterName) + " ("
                + characterClass + ", level " + level + ").\n");
        output.write("Commands: character-select, character-delete " + characterName + ", logout\n");
    }
}
