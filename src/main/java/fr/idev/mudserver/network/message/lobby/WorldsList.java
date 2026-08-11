package fr.idev.mudserver.network.message.lobby;

import java.util.List;

import fr.idev.mudserver.domain.actor.CharacterClass;
import fr.idev.mudserver.telnet.Ansi;
import fr.idev.mudserver.telnet.OutputTelnetMessage;
import fr.idev.mudserver.telnet.TelnetOutput;

public record WorldsList(List<Entry> worlds) implements OutputTelnetMessage {

    /**
     * {@code existingCharacterName} vaut {@code null} quand ce compte n'a pas
     * encore de personnage dans ce monde — pas de sous-type dédié : un seul champ
     * optionnel suffit ici plutôt que deux records "un par issue", vu qu'une seule
     * ligne du tableau varie.
     */
    public record Entry(String shortName, String name, String description, int minPlayers, int maxPlayers,
            String existingCharacterName, CharacterClass existingCharacterClass, Integer existingCharacterLevel) {
    }

    @Override
    public void toTelnet(TelnetOutput output) {
        if (worlds.isEmpty()) {
            output.write("No world available.\n");
            return;
        }

        for (Entry entry : worlds) {
            output.write(entry.shortName() + " - " + entry.name() + " (" + entry.minPlayers() + "-" + entry.maxPlayers()
                    + " players): " + entry.description() + "\n");
            if (entry.existingCharacterName() != null) {
                output.write("  you have a character here: " + Ansi.player(entry.existingCharacterName()) + " ("
                        + entry.existingCharacterClass() + ", level " + entry.existingCharacterLevel() + ")\n");
            }
        }
        output.write("Commands: world-enter <short-name>, logout\n");
    }
}
