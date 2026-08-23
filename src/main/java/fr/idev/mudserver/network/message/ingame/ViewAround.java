package fr.idev.mudserver.network.message.ingame;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import fr.idev.mudserver.network.OutputJsonMessage;
import fr.idev.mudserver.domain.actor.AbstractCharacter;
import fr.idev.mudserver.domain.actor.AbstractNpc;
import fr.idev.mudserver.domain.actor.instance.CharacterInstance;
import fr.idev.mudserver.domain.actor.instance.MonsterInstance;
import fr.idev.mudserver.domain.map.HexCoordinate;
import fr.idev.mudserver.domain.world.ZoneInstance;
import fr.idev.mudserver.game.engine.MovementEngine;
import fr.idev.mudserver.network.server.telnet.Ansi;
import fr.idev.mudserver.network.server.telnet.OutputTelnetMessage;
import fr.idev.mudserver.network.server.telnet.TelnetOutput;
import fr.idev.mudserver.network.server.tui.JsonOutput;

public record ViewAround(AbstractCharacter character) implements OutputTelnetMessage, OutputJsonMessage {

    public static final int VIEWPORT_RADIUS = 5;

    public static final String LEGEND = "@ = you   p = other player   m = monster   n = npc   # = portal   "
            + ". = floor   % = wall   ~ = out of bounds   X = destination   - = path";

    private static final String MAP_HEADER = "──────── Map ────────";

    public record CellView(int q, int r, String kind) {
    }

    public record PortalView(String direction, String targetZoneName) {
    }

    public record Payload(String zoneName, String zoneDescription, List<CellView> cells, List<PortalView> portals,
            List<String> charactersNearby, List<String> monstersNearby, List<String> npcsNearby) {
    }

    @Override
    public void toJson(JsonOutput output) {
        ZoneInstance zone = character.getCurrentZone();
        List<AbstractCharacter> nearby = zone.occupantsWithin(character.getPosition(), VIEWPORT_RADIUS);

        List<PortalView> portals = zone.getPortals().stream()
                .map(portal -> new PortalView(portal.direction().toString(), portal.targetZone().getName())).toList();
        List<String> characterNames = nearby.stream().filter(CharacterInstance.class::isInstance)
                .filter(other -> !other.getId().equals(character.getId())).map(AbstractCharacter::getName).toList();
        List<String> monsterNames = nearby.stream().filter(MonsterInstance.class::isInstance)
                .map(AbstractCharacter::getName).toList();
        List<String> npcNames = nearby.stream().filter(AbstractNpc.class::isInstance).map(AbstractCharacter::getName)
                .toList();

        output.write("ViewAround", new Payload(zone.getName(), zone.getDescription(), renderCells(zone, character),
                portals, characterNames, monsterNames, npcNames), false);
    }

    private List<CellView> renderCells(ZoneInstance zone, AbstractCharacter viewer) {
        HexCoordinate center = viewer.getPosition();
        List<HexCoordinate> path = remainingPath(viewer);
        Set<HexCoordinate> pathCells = new HashSet<>(path);
        HexCoordinate destination = path.isEmpty() ? null : path.getLast();
        List<CellView> cells = new ArrayList<>();

        for (int r = center.r() - VIEWPORT_RADIUS; r <= center.r() + VIEWPORT_RADIUS; r++) {
            int dr = r - center.r();
            int dqMin = Math.max(-VIEWPORT_RADIUS, -dr - VIEWPORT_RADIUS);
            int dqMax = Math.min(VIEWPORT_RADIUS, -dr + VIEWPORT_RADIUS);
            for (int dq = dqMin; dq <= dqMax; dq++) {
                HexCoordinate cell = new HexCoordinate(center.q() + dq, r);
                cells.add(new CellView(cell.q(), cell.r(), kindFor(zone, viewer, cell, pathCells, destination)));
            }
        }
        return cells;
    }

    private String kindFor(ZoneInstance zone, AbstractCharacter viewer, HexCoordinate cell,
            Set<HexCoordinate> pathCells, HexCoordinate destination) {
        if (cell.equals(viewer.getPosition())) {
            return "self";
        }
        if (!zone.containsCell(cell)) {
            return "outOfBounds";
        }
        if (!zone.isWalkable(cell)) {
            return "blocked";
        }

        Optional<AbstractCharacter> occupant = zone.occupantAt(cell);
        if (occupant.isPresent()) {
            return switch (occupant.get()) {
                case CharacterInstance ignored -> "player";
                case MonsterInstance ignored -> "monster";
                case AbstractNpc ignored -> "npc";
                default -> throw new IllegalStateException("Type d'occupant inattendu : " + occupant.get().getClass());
            };
        }

        if (cell.equals(destination)) {
            return zone.findPortalAt(cell).isPresent() ? "portalDestination" : "destination";
        }
        if (pathCells.contains(cell)) {
            return "path";
        }
        if (zone.findPortalAt(cell).isPresent()) {
            return "portal";
        }
        return "floor";
    }

    @Override
    public void toTelnet(TelnetOutput output) {
        ZoneInstance zone = character.getCurrentZone();

        List<String> gridLines = render(zone, character);
        List<AbstractCharacter> nearby = zone.occupantsWithin(character.getPosition(), VIEWPORT_RADIUS);

        List<String> portalSummaries = zone.getPortals().stream()
                .map(portal -> portal.direction() + ": " + portal.targetZone().getName()).toList();
        List<String> characterNames = nearby.stream().filter(CharacterInstance.class::isInstance)
                .filter(other -> !other.getId().equals(character.getId())).map(AbstractCharacter::getName).toList();
        List<String> monsterNames = nearby.stream().filter(MonsterInstance.class::isInstance)
                .map(AbstractCharacter::getName).toList();
        List<String> npcNames = nearby.stream().filter(AbstractNpc.class::isInstance).map(AbstractCharacter::getName)
                .toList();

        String coloredGrid = gridLines.stream().map(Ansi::gridLine).collect(Collectors.joining("\n"));
        output.write(String.format(
                "== %s ==\n%s\n\n%s\n%s\n\n%s\n\nPortals: %s\nCharacters here: %s\nMonsters: %s\nNPCs: %s\n",
                Ansi.zone(zone.getName()), zone.getDescription(), Ansi.zone(MAP_HEADER), coloredGrid,
                Ansi.gridLegend(LEGEND), portalSummaries.isEmpty() ? "none." : String.join(", ", portalSummaries),
                joinColored(characterNames, Ansi::player, "no one else."),
                joinColored(monsterNames, Ansi::monster, "none."), joinColored(npcNames, Ansi::npc, "none.")));
    }

    private static <T> String joinColored(List<T> values, Function<T, String> colorize, String whenEmpty) {
        return values.isEmpty() ? whenEmpty : values.stream().map(colorize).collect(Collectors.joining(", "));
    }

    private List<String> render(ZoneInstance zone, AbstractCharacter viewer) {
        return render(zone, viewer, VIEWPORT_RADIUS);
    }

    private List<String> render(ZoneInstance zone, AbstractCharacter viewer, int radius) {
        HexCoordinate center = viewer.getPosition();
        List<HexCoordinate> path = remainingPath(viewer);
        Set<HexCoordinate> pathCells = new HashSet<>(path);
        HexCoordinate destination = path.isEmpty() ? null : path.getLast();
        List<String> lines = new ArrayList<>();

        for (int r = center.r() - radius; r <= center.r() + radius; r++) {
            int dr = r - center.r();
            int dqMin = Math.max(-radius, -dr - radius);
            int dqMax = Math.min(radius, -dr + radius);

            StringBuilder line = new StringBuilder();
            if (Math.floorMod(r, 2) == 1) {
                line.append(' ');
            }
            for (int dq = dqMin; dq <= dqMax; dq++) {
                HexCoordinate cell = new HexCoordinate(center.q() + dq, r);
                if (dq > dqMin) {
                    line.append(' ');
                }
                line.append(glyphFor(zone, viewer, cell, pathCells, destination));
            }
            lines.add(line.toString());
        }
        return lines;
    }

    private char glyphFor(ZoneInstance zone, AbstractCharacter viewer, HexCoordinate cell, Set<HexCoordinate> pathCells,
            HexCoordinate destination) {
        if (cell.equals(viewer.getPosition())) {
            return '@';
        }
        if (!zone.containsCell(cell)) {
            return '~';
        }
        if (!zone.isWalkable(cell)) {
            return '%';
        }

        Optional<AbstractCharacter> occupant = zone.occupantAt(cell);
        if (occupant.isPresent()) {
            return switch (occupant.get()) {
                case CharacterInstance ignored -> 'p';
                case MonsterInstance ignored -> 'm';
                case AbstractNpc ignored -> 'n';
                default -> throw new IllegalStateException("Type d'occupant inattendu : " + occupant.get().getClass());
            };
        }

        if (cell.equals(destination)) {
            // '*' est un glyphe interne, jamais affiché tel quel : Ansi.gridLine le
            // traduit en 'X' coloré comme un portail, pour que la case d'arrivée
            // reste cohérente visuellement quand elle est elle-même un portail.
            return zone.findPortalAt(cell).isPresent() ? '*' : 'X';
        }
        if (pathCells.contains(cell)) {
            return '-';
        }

        if (zone.findPortalAt(cell).isPresent()) {
            return '#';
        }

        return '.';
    }

    private List<HexCoordinate> remainingPath(AbstractCharacter viewer) {
        MovementEngine.ActiveMovement movement = viewer.activeMovement;
        if (movement == null) {
            return List.of();
        }

        List<HexCoordinate> path = new ArrayList<>(movement.cellsRemaining());
        HexCoordinate cursor = viewer.getPosition();
        for (int i = 0; i < movement.cellsRemaining(); i++) {
            cursor = cursor.neighbor(movement.direction());
            path.add(cursor);
        }

        return path;
    }
}
