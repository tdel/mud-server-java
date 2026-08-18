package fr.idev.mudserver.network.message.ingame;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import fr.idev.mudserver.domain.actor.AbstractCharacter;
import fr.idev.mudserver.domain.actor.AbstractNpc;
import fr.idev.mudserver.domain.actor.AbstractObject;
import fr.idev.mudserver.domain.actor.component.IdentityComponent;
import fr.idev.mudserver.domain.actor.component.MovementComponent;
import fr.idev.mudserver.domain.actor.component.PositionComponent;
import fr.idev.mudserver.domain.actor.instance.CharacterInstance;
import fr.idev.mudserver.domain.actor.instance.MonsterInstance;
import fr.idev.mudserver.domain.map.HexCoordinate;
import fr.idev.mudserver.domain.map.HexDirection;
import fr.idev.mudserver.domain.world.RoomInstance;
import fr.idev.mudserver.telnet.Ansi;
import fr.idev.mudserver.telnet.OutputTelnetMessage;
import fr.idev.mudserver.telnet.TelnetOutput;

public record ViewAround(AbstractObject character) implements OutputTelnetMessage {

    public static final int VIEWPORT_RADIUS = 5;

    public static final String LEGEND = "@ = you   p = other player   m = monster   n = npc   # = portal   "
            + ". = floor   ~ = out of bounds   X = destination   - = path";

    private static final String MAP_HEADER = "──────── Map ────────";

    @Override
    public void toTelnet(TelnetOutput output) {

        RoomInstance room = character.component(PositionComponent.class).currentRoom;

        List<String> gridLines = render(room, character);
        List<AbstractCharacter> nearby = room
                .occupantsWithin(character.component(PositionComponent.class).hexCoordinate, VIEWPORT_RADIUS);

        List<String> portalSummaries = room.getPortals().stream()
                .map(portal -> portal.direction() + ": " + portal.targetRoom().getName()).toList();
        List<String> characterNames = nearby.stream().filter(CharacterInstance.class::isInstance)
                .filter(other -> !other.getId().equals(character.getId()))
                .map(other -> other.component(IdentityComponent.class).name).toList();
        List<String> monsterNames = nearby.stream().filter(MonsterInstance.class::isInstance)
                .map(other -> other.component(IdentityComponent.class).name).toList();
        List<String> npcNames = nearby.stream().filter(AbstractNpc.class::isInstance)
                .map(other -> other.component(IdentityComponent.class).name).toList();

        String coloredGrid = gridLines.stream().map(Ansi::gridLine).collect(Collectors.joining("\n"));
        output.write(String.format(
                "== %s ==\n%s\n\n%s\n%s\n\n%s\n\nPortals: %s\nCharacters here: %s\nMonsters: %s\nNPCs: %s\n",
                Ansi.room(room.getName()), room.getDescription(), Ansi.room(MAP_HEADER), coloredGrid,
                Ansi.gridLegend(LEGEND), portalSummaries.isEmpty() ? "none." : String.join(", ", portalSummaries),
                joinColored(characterNames, Ansi::player, "no one else."),
                joinColored(monsterNames, Ansi::monster, "none."), joinColored(npcNames, Ansi::npc, "none.")));
    }

    private static <T> String joinColored(List<T> values, Function<T, String> colorize, String whenEmpty) {
        return values.isEmpty() ? whenEmpty : values.stream().map(colorize).collect(Collectors.joining(", "));
    }

    private List<String> render(RoomInstance room, AbstractObject viewer) {
        return render(room, viewer, VIEWPORT_RADIUS);
    }

    private List<String> render(RoomInstance room, AbstractObject viewer, int radius) {
        HexCoordinate center = viewer.component(PositionComponent.class).hexCoordinate;
        List<HexCoordinate> path = remainingPath(viewer);
        Set<HexCoordinate> pathCells = new HashSet<>(path);
        HexCoordinate destination = path.isEmpty() ? null : path.get(path.size() - 1);
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
                line.append(glyphFor(room, viewer, cell, pathCells, destination));
            }
            lines.add(line.toString());
        }
        return lines;
    }

    private char glyphFor(RoomInstance room, AbstractObject viewer, HexCoordinate cell, Set<HexCoordinate> pathCells,
            HexCoordinate destination) {
        if (cell.equals(viewer.component(PositionComponent.class).hexCoordinate)) {
            return '@';
        }
        if (!room.isInBounds(cell)) {
            return '~';
        }

        Optional<AbstractCharacter> occupant = room.occupantAt(cell);
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
            return room.findPortalAt(cell).isPresent() ? '*' : 'X';
        }
        if (pathCells.contains(cell)) {
            return '-';
        }

        if (room.findPortalAt(cell).isPresent()) {
            return '#';
        }

        return '.';
    }

    private List<HexCoordinate> remainingPath(AbstractObject character) {
        Optional<MovementComponent> movement = character.findComponent(MovementComponent.class);
        if (movement.isEmpty()) {
            return List.of();
        }
        int cellsRemaining = movement.get().cellsRemaining;
        HexDirection direction = movement.get().direction;
        List<HexCoordinate> path = new ArrayList<>(cellsRemaining);
        HexCoordinate cursor = character.component(PositionComponent.class).hexCoordinate;
        for (int i = 0; i < cellsRemaining; i++) {
            cursor = cursor.neighbor(direction);
            path.add(cursor);
        }
        return path;
    }
}
