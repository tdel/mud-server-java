package fr.idev.mudserver.game;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import fr.idev.mudserver.domain.map.HexCoordinate;
import fr.idev.mudserver.domain.world.RoomInstance;
import fr.idev.mudserver.domain.actor.AbstractCharacter;
import fr.idev.mudserver.domain.actor.instance.MonsterInstance;
import fr.idev.mudserver.domain.actor.AbstractNpc;
import fr.idev.mudserver.domain.actor.instance.CharacterInstance;
import fr.idev.mudserver.domain.actor.system.MovementSystem;

public final class HexGridRenderer {

    public static final int VIEWPORT_RADIUS = 5;

    public static final String LEGEND = "@ = you   p = other player   m = monster   n = npc   # = portal   "
            + ". = floor   ~ = out of bounds   X = destination   - = path";

    private HexGridRenderer() {
    }

    public static List<String> render(RoomInstance room, CharacterInstance viewer) {
        return render(room, viewer, VIEWPORT_RADIUS);
    }

    static List<String> render(RoomInstance room, CharacterInstance viewer, int radius) {
        HexCoordinate center = viewer.getPosition();
        List<HexCoordinate> path = MovementSystem.remainingPath(viewer);
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

    private static char glyphFor(RoomInstance room, CharacterInstance viewer, HexCoordinate cell,
            Set<HexCoordinate> pathCells, HexCoordinate destination) {
        if (cell.equals(viewer.getPosition())) {
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
}
