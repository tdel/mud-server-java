package fr.idev.mudserver.game.catalog.tiled;

import fr.idev.mudserver.domain.map.HexCoordinate;

/**
 * Convertit entre les coordonnées "offset" utilisées par les cartes hexagonales
 * exportées par Tiled Map Editor (index colonne/ligne dans un tableau 2D, plus
 * la position pixel des objets) et les coordonnées axiales
 * {@link HexCoordinate} déjà utilisées partout ailleurs dans le projet (q, r —
 * confirmé axial par {@code HexCoordinate.distanceTo} et les vecteurs de
 * {@code HexDirection}).
 *
 * <p>
 * Seule la configuration Tiled {@code orientation: "hexagonal"},
 * {@code staggeraxis: "y"}, {@code staggerindex: "odd"} est supportée
 * (hexagones "pointy top", lignes impaires décalées vers la droite) — c'est la
 * seule qui correspond au système de coordonnées déjà en place (voir le
 * décalage d'une demi-case sur les lignes impaires dans
 * {@code ViewAround.render}).
 */
public final class HexTiledCoordinateMapper {

    private HexTiledCoordinateMapper() {
    }

    public static HexCoordinate offsetToAxial(int col, int row) {
        int q = col - (row - (row & 1)) / 2;
        return new HexCoordinate(q, row);
    }

    public static int[] axialToOffset(HexCoordinate cell) {
        int row = cell.r();
        int col = cell.q() + (row - (row & 1)) / 2;
        return new int[]{col, row};
    }

    public static double[] cellCenterPixel(int col, int row, int tilewidth, int tileheight, int hexsidelength) {
        double rowHeight = (tileheight + hexsidelength) / 2.0;
        boolean oddRow = (row & 1) == 1;
        double x = col * tilewidth + (oddRow ? tilewidth / 2.0 : 0) + tilewidth / 2.0;
        double y = row * rowHeight + tileheight / 2.0;
        return new double[]{x, y};
    }

    public static HexCoordinate pixelToAxial(double x, double y, int tilewidth, int tileheight, int hexsidelength) {
        double rowHeight = (tileheight + hexsidelength) / 2.0;
        int row = (int) Math.round((y - tileheight / 2.0) / rowHeight);
        boolean oddRow = (row & 1) == 1;
        double adjustedX = x - tilewidth / 2.0 - (oddRow ? tilewidth / 2.0 : 0);
        int col = (int) Math.round(adjustedX / tilewidth);
        return offsetToAxial(col, row);
    }
}
