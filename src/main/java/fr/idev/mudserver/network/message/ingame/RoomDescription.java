package fr.idev.mudserver.network.message.ingame;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import fr.idev.mudserver.domain.Rarity;
import fr.idev.mudserver.telnet.Ansi;
import fr.idev.mudserver.telnet.OutputTelnetMessage;
import fr.idev.mudserver.telnet.TelnetOutput;

/**
 * {@code gridLines}/{@code legend} portent le viewport hexagonal construit par
 * {@code game.HexGridRenderer} (rayon fixe autour du personnage, jamais la
 * grille entière). {@code characterNames}/{@code monsterNames}/{@code npcNames}
 * sont désormais bornés à ce même viewport, cohérents avec ce que la grille
 * affiche ; {@code items} reste à l'échelle de la room entière (les items n'ont
 * pas de position en case dans cette phase). {@code portalSummaries} remplace
 * l'ancien {@code exitNames} et reste lui aussi à l'échelle de la room entière
 * (pas de "brouillard de guerre") — nécessaire pour que la navigation reste
 * praticable dans une grille 64x64.
 */
public record RoomDescription(String roomName, String description, List<String> gridLines, String legend,
        List<String> portalSummaries, List<String> characterNames, List<ItemSummary> items, List<String> monsterNames,
        List<String> npcNames) implements OutputTelnetMessage {

    private static final String MAP_HEADER = "──────── Map ────────";

    public record ItemSummary(String name, Rarity rarity) {
    }

    @Override
    public void toTelnet(TelnetOutput output) {
        String coloredGrid = gridLines.stream().map(Ansi::gridLine).collect(Collectors.joining("\n"));
        output.write(String.format(
                "== %s ==\n%s\n\n%s\n%s\n\n%s\n\nPortals: %s\nCharacters here: %s\nItems: %s\nMonsters: %s\nNPCs: %s\n",
                Ansi.room(roomName), description, Ansi.room(MAP_HEADER), coloredGrid, Ansi.gridLegend(legend),
                portalSummaries.isEmpty() ? "none." : String.join(", ", portalSummaries),
                joinColored(characterNames, Ansi::player, "no one else."),
                joinColored(items, item -> Ansi.item(item.name(), item.rarity()), "none."),
                joinColored(monsterNames, Ansi::monster, "none."), joinColored(npcNames, Ansi::npc, "none.")));
    }

    private static <T> String joinColored(List<T> values, Function<T, String> colorize, String whenEmpty) {
        return values.isEmpty() ? whenEmpty : values.stream().map(colorize).collect(Collectors.joining(", "));
    }
}
