package app.game.catalog.tiled;

import java.util.List;

/**
 * Sous-ensemble du format {@code .tmx} (XML natif Tiled Map Editor, orientation
 * orthogonale) réellement exploité par {@link TiledMapLoader}. Les autres
 * attributs/balises standards d'un fichier Tiled (version, tiledversion,
 * infinite, renderorder, nextlayerid, nextobjectid, image du tileset, etc.)
 * restent présents dans les fichiers authorés sous {@code data/maps/*.tmx}
 * pour rester ouvrables tels quels dans Tiled, mais ne sont pas modélisés ici :
 * {@link TiledMapLoader} ignore simplement ce qu'il ne lit pas.
 */
public record TiledMap(String orientation, int width, int height, int tilewidth, int tileheight,
        List<TiledLayer> layers, List<TiledTileset> tilesets, List<TiledProperty> properties) {

    public record TiledLayer(String type, String name, Integer width, Integer height, List<Integer> data,
            List<TiledObjectDef> objects, List<TiledProperty> properties) {
    }

    public record TiledObjectDef(int id, String name, String type, double x, double y, List<TiledPoint> polygonPoints,
            List<TiledProperty> properties) {
    }

    /** Point d'un {@code <polygon points="x1,y1 x2,y2 ...">}, relatif à x/y de l'objet qui le porte. */
    public record TiledPoint(double x, double y) {
    }

    public record TiledTileset(int firstgid, String source, List<TiledTile> tiles) {
    }

    public record TiledTile(int id, List<TiledProperty> properties) {
    }

    public record TiledProperty(String name, String type, Object value) {
    }
}
