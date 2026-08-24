package fr.idev.mudserver.game.catalog.tiled;

import fr.idev.mudserver.domain.map.Position;

/**
 * Convertit les positions pixel d'un export Tiled Map Editor orthogonal en
 * positions continues du monde du jeu. Aucune quantification à une cellule :
 * les objets Tiled (spawns, portails) gardent leur position exacte.
 */
public final class TiledCoordinateMapper {

    public static final double PIXELS_PER_UNIT = 32.0;

    private TiledCoordinateMapper() {
    }

    public static Position pixelToWorld(double x, double y) {
        return new Position(x / PIXELS_PER_UNIT, y / PIXELS_PER_UNIT);
    }
}
