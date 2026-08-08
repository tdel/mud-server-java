package fr.idev.mudserver.network.message.ingame;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import fr.idev.mudserver.telnet.Ansi;
import fr.idev.mudserver.telnet.OutputTelnetMessage;
import fr.idev.mudserver.telnet.TelnetOutput;

/**
 * {@code gridLines}/{@code legend} portent le viewport hexagonal construit par
 * {@code game.HexGridRenderer} (rayon fixe autour du personnage, jamais la
 * grille entière). {@code characterNames}/{@code monsterNames}/{@code npcNames}
 * sont désormais bornés à ce même viewport, cohérents avec ce que la grille
 * affiche ; {@code itemNames} reste à l'échelle de la room entière (les items
 * n'ont pas de position en case dans cette phase). {@code portalSummaries}
 * remplace l'ancien {@code exitNames} et reste lui aussi à l'échelle de la room
 * entière (pas de "brouillard de guerre") — nécessaire pour que la navigation
 * reste praticable dans une grille 64x64.
 */
public record RoomDescription(String roomName, String description, List<String> gridLines, String legend,
        List<String> portalSummaries, List<String> characterNames, List<String> itemNames, List<String> monsterNames,
        List<String> npcNames) implements OutputTelnetMessage {

    @Override
    public void toTelnet(TelnetOutput output) {
        output.write(String.format(
                "== %s ==\n%s\n\n%s\n\n%s\n\nPortals: %s\nCharacters here: %s\nItems: %s\nMonsters: %s\nNPCs: %s\n",
                Ansi.room(roomName), description, String.join("\n", gridLines), legend,
                portalSummaries.isEmpty() ? "none." : String.join(", ", portalSummaries),
                joinColored(characterNames, Ansi::player, "no one else."), joinColored(itemNames, Ansi::item, "none."),
                joinColored(monsterNames, Ansi::monster, "none."), joinColored(npcNames, Ansi::npc, "none.")));
    }

    private static String joinColored(List<String> names, Function<String, String> colorize, String whenEmpty) {
        return names.isEmpty() ? whenEmpty : names.stream().map(colorize).collect(Collectors.joining(", "));
    }
}
