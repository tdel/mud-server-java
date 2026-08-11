package fr.idev.mudserver.game;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import fr.idev.mudserver.domain.HexCoordinate;
import fr.idev.mudserver.domain.RoomInstance;
import fr.idev.mudserver.domain.actor.GameCharacter;
import fr.idev.mudserver.domain.actor.GameMonster;
import fr.idev.mudserver.domain.actor.GameNpc;
import fr.idev.mudserver.domain.actor.GamePlayer;

/**
 * Construit le viewport texte affiché par {@code controller.ingame.Look} : un
 * rayon fixe de cases (jamais la grille entière) centré sur la position du
 * personnage, pour que la sortie reste bornée quelle que soit la taille de la
 * room (16x8 comme 64x64). Priorité d'affichage en cas de cumul sur une case :
 * soi &gt; autre joueur &gt; monstre &gt; PNJ &gt; portail &gt; sol &gt;
 * hors-grille.
 */
public final class HexGridRenderer {

    public static final int VIEWPORT_RADIUS = 5;

    public static final String LEGEND = "@ = you   p = other player   m = monster   n = npc   # = portal   "
            + ". = floor   ~ = out of bounds";

    private HexGridRenderer() {
    }

    public static List<String> render(RoomInstance room, GamePlayer viewer) {
        return render(room, viewer, VIEWPORT_RADIUS);
    }

    static List<String> render(RoomInstance room, GamePlayer viewer, int radius) {
        HexCoordinate center = viewer.getPosition();
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
                line.append(glyphFor(room, viewer, cell));
            }
            lines.add(line.toString());
        }
        return lines;
    }

    private static char glyphFor(RoomInstance room, GamePlayer viewer, HexCoordinate cell) {
        if (cell.equals(viewer.getPosition())) {
            return '@';
        }
        if (!room.isInBounds(cell)) {
            return '~';
        }

        Optional<GameCharacter> occupant = room.occupantAt(cell);
        if (occupant.isPresent()) {
            return switch (occupant.get()) {
                case GamePlayer ignored -> 'p';
                case GameMonster ignored -> 'm';
                case GameNpc ignored -> 'n';
            };
        }

        if (room.findPortalAt(cell).isPresent()) {
            return '#';
        }

        return '.';
    }
}
