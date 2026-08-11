package fr.idev.mudserver.network.message.charselect;

import fr.idev.mudserver.domain.actor.CharacterClass;
import fr.idev.mudserver.telnet.Ansi;
import fr.idev.mudserver.telnet.OutputTelnetMessage;
import fr.idev.mudserver.telnet.TelnetOutput;

public record ExistingCharacterInWorld(String worldName, String characterName, CharacterClass characterClass,
        int level) implements OutputTelnetMessage {

    @Override
    public void toTelnet(TelnetOutput output) {
        output.write("You already have a character in " + worldName + ": " + Ansi.player(characterName) + " ("
                + characterClass + ", level " + level + ").\n");
        output.write("Commands: character-select, character-delete " + characterName + ", logout\n");
    }
}
