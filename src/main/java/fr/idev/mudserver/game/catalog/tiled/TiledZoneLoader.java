package fr.idev.mudserver.game.catalog.tiled;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import fr.idev.mudserver.domain.MonsterSpawn;
import fr.idev.mudserver.domain.NpcSpawn;
import fr.idev.mudserver.domain.map.Position;
import fr.idev.mudserver.domain.world.CollisionGrid;
import fr.idev.mudserver.domain.world.TileType;
import fr.idev.mudserver.game.catalog.tiled.TiledMap.TiledLayer;
import fr.idev.mudserver.game.catalog.tiled.TiledMap.TiledObjectDef;
import fr.idev.mudserver.game.catalog.tiled.TiledMap.TiledProperty;
import fr.idev.mudserver.game.catalog.tiled.TiledMap.TiledTile;
import fr.idev.mudserver.game.catalog.tiled.TiledMap.TiledTileset;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Convertit un {@link TiledMap} (arbre JSON Jackson reflétant un export Tiled
 * Map Editor, orientation orthogonale) en données de zone exploitables par
 * {@code WorldTemplateCatalog}. Un fichier par zone : un calque de tuiles nommé
 * "terrain" porte le terrain praticable/bloqué (propriété custom booléenne
 * "walkable" sur chaque tuile du tileset), un calque d'objets nommé "objects"
 * porte le point de spawn du joueur, les portails et les spawns de monstres.
 */
public final class TiledZoneLoader {

    private static final String TERRAIN_LAYER = "terrain";
    private static final String OBJECTS_LAYER = "objects";
    private static final String TYPE_PLAYER_SPAWN = "playerSpawn";
    private static final String TYPE_PORTAL = "portal";
    private static final String TYPE_MONSTER_SPAWN = "monsterSpawn";
    private static final String TYPE_NPC_SPAWN = "npcSpawn";
    private static final double DEFAULT_PORTAL_TRIGGER_RADIUS = 0.6;
    private static final double COLLISION_CELL_SIZE = 1.0;

    // Un export Tiled porte des champs standards (version, tiledversion, infinite,
    // renderorder, image du tileset, etc.) qu'on ne modélise pas dans TiledMap :
    // ce mapper dédié les tolère plutôt que d'échouer, sans toucher au bean
    // ObjectMapper partagé (utilisé ailleurs pour les autres data/*.json, où une
    // propriété inconnue doit rester une erreur signalante).
    private static final ObjectMapper TILED_MAPPER = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();

    private TiledZoneLoader() {
    }

    public static TiledMap readMap(InputStream in) {
        try {
            return TILED_MAPPER.readValue(in, TiledMap.class);
        } catch (JacksonException e) {
            throw new IllegalStateException("Impossible de lire un fichier de zone Tiled", e);
        }
    }

    public record ParsedZone(UUID id, String name, String description, boolean isStartingZone, CollisionGrid terrain,
            Position spawnPosition, List<MonsterSpawn> monsterSpawns, List<NpcSpawn> npcSpawns,
            List<PortalDraft> portals) {
    }

    public record PortalDraft(Position position, String direction, UUID targetZoneId, Position targetPosition,
            double triggerRadius) {
    }

    public static ParsedZone parse(TiledMap map, Function<String, InputStream> externalTilesetResolver) {
        UUID id = UUID.fromString(requireStringProperty(map.properties(), "id"));
        String name = requireStringProperty(map.properties(), "name");
        String description = requireStringProperty(map.properties(), "description");
        boolean isStartingZone = booleanProperty(map.properties(), "isStartingZone", false);

        Map<Integer, TileType> tileTypeByGid = buildTileTypeByGid(map, externalTilesetResolver);
        CollisionGrid terrain = parseTerrain(map, tileTypeByGid, id);

        Position[] spawnPositionHolder = new Position[1];
        List<MonsterSpawn> monsterSpawns = new ArrayList<>();
        List<NpcSpawn> npcSpawns = new ArrayList<>();
        List<PortalDraft> portals = new ArrayList<>();
        parseObjects(map, id, spawnPositionHolder, monsterSpawns, npcSpawns, portals);

        if (spawnPositionHolder[0] == null) {
            throw new IllegalStateException("Zone " + id + " (" + name + ") n'a aucun objet playerSpawn");
        }

        return new ParsedZone(id, name, description, isStartingZone, terrain, spawnPositionHolder[0],
                List.copyOf(monsterSpawns), List.copyOf(npcSpawns), List.copyOf(portals));
    }

    private static Map<Integer, TileType> buildTileTypeByGid(TiledMap map,
            Function<String, InputStream> externalTilesetResolver) {
        Map<Integer, TileType> byGid = new HashMap<>();
        for (TiledTileset tileset : map.tilesets()) {
            List<TiledTile> tiles = tileset.tiles() != null
                    ? tileset.tiles()
                    : readExternalTileset(tileset.source(), externalTilesetResolver);
            for (TiledTile tile : tiles) {
                boolean walkable = booleanProperty(tile.properties(), "walkable", true);
                byGid.put(tileset.firstgid() + tile.id(), walkable ? TileType.FLOOR : TileType.WALL);
            }
        }
        return byGid;
    }

    // Tileset externe partagé entre zones (data/zones/shared/*.tsx), au format
    // Tiled XML natif
    // (ouvrable tel quel dans Tiled Map Editor) — on n'en extrait que les
    // <tile>/<properties>
    // utiles, le reste (image, colonnes, etc.) n'étant pas exploité côté serveur.
    private static List<TiledTile> readExternalTileset(String source,
            Function<String, InputStream> externalTilesetResolver) {
        try (InputStream in = externalTilesetResolver.apply(source)) {
            DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            NodeList tileNodes = builder.parse(in).getElementsByTagName("tile");
            List<TiledTile> tiles = new ArrayList<>();
            for (int i = 0; i < tileNodes.getLength(); i++) {
                Element tileElement = (Element) tileNodes.item(i);
                int id = Integer.parseInt(tileElement.getAttribute("id"));
                tiles.add(new TiledTile(id, readXmlProperties(tileElement)));
            }
            return tiles;
        } catch (Exception e) {
            throw new IllegalStateException("Impossible de charger le tileset externe " + source, e);
        }
    }

    private static List<TiledProperty> readXmlProperties(Element tileElement) {
        List<TiledProperty> properties = new ArrayList<>();
        NodeList propertyNodes = tileElement.getElementsByTagName("property");
        for (int i = 0; i < propertyNodes.getLength(); i++) {
            Element propertyElement = (Element) propertyNodes.item(i);
            String name = propertyElement.getAttribute("name");
            String type = propertyElement.hasAttribute("type") ? propertyElement.getAttribute("type") : "string";
            Object value = "bool".equals(type)
                    ? Boolean.valueOf(propertyElement.getAttribute("value"))
                    : propertyElement.getAttribute("value");
            properties.add(new TiledProperty(name, type, value));
        }
        return properties;
    }

    private static CollisionGrid parseTerrain(TiledMap map, Map<Integer, TileType> tileTypeByGid, UUID id) {
        TiledLayer layer = findLayer(map, id, TERRAIN_LAYER, "tilelayer");
        int width = layer.width();
        int height = layer.height();
        BitSet walkable = new BitSet(width * height);
        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                int gid = layer.data().get(row * width + col);
                TileType tileType = gid == 0 ? TileType.WALL : tileTypeByGid.getOrDefault(gid, TileType.FLOOR);
                walkable.set(row * width + col, tileType.isWalkable());
            }
        }
        return new CollisionGrid(width, height, COLLISION_CELL_SIZE, walkable);
    }

    private static void parseObjects(TiledMap map, UUID id, Position[] spawnPositionHolder,
            List<MonsterSpawn> monsterSpawns, List<NpcSpawn> npcSpawns, List<PortalDraft> portals) {
        TiledLayer layer = findLayer(map, id, OBJECTS_LAYER, "objectgroup");
        for (TiledObjectDef object : layer.objects()) {
            Position position = TiledCoordinateMapper.pixelToWorld(object.x(), object.y());
            switch (object.type()) {
                case TYPE_PLAYER_SPAWN -> spawnPositionHolder[0] = position;
                case TYPE_PORTAL ->
                    portals.add(new PortalDraft(position, requireStringProperty(object.properties(), "direction"),
                            UUID.fromString(requireStringProperty(object.properties(), "targetZoneId")),
                            TiledCoordinateMapper.pixelToWorld(doubleProperty(object.properties(), "targetX"),
                                    doubleProperty(object.properties(), "targetY")),
                            doubleProperty(object.properties(), "triggerRadius", DEFAULT_PORTAL_TRIGGER_RADIUS)));
                case TYPE_MONSTER_SPAWN -> monsterSpawns.add(new MonsterSpawn(
                        UUID.nameUUIDFromBytes((id + ":" + object.id()).getBytes(StandardCharsets.UTF_8)),
                        UUID.fromString(requireStringProperty(object.properties(), "templateId")), position));
                case TYPE_NPC_SPAWN -> npcSpawns.add(
                        new NpcSpawn(UUID.nameUUIDFromBytes((id + ":" + object.id()).getBytes(StandardCharsets.UTF_8)),
                                UUID.fromString(requireStringProperty(object.properties(), "npcId")), position));
                default -> throw new IllegalStateException(
                        "Objet Tiled " + object.id() + " a un type inconnu : " + object.type());
            }
        }
    }

    private static TiledLayer findLayer(TiledMap map, UUID id, String name, String type) {
        return map.layers().stream().filter(l -> name.equals(l.name()) && type.equals(l.type())).findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Calque \"" + name + "\" (" + type + ") absent de la zone " + id));
    }

    private static String requireStringProperty(List<TiledProperty> properties, String name) {
        return stringProperty(properties, name)
                .orElseThrow(() -> new IllegalStateException("Propriété Tiled \"" + name + "\" manquante"));
    }

    private static Optional<String> stringProperty(List<TiledProperty> properties, String name) {
        if (properties == null) {
            return Optional.empty();
        }
        return properties.stream().filter(p -> p.name().equals(name)).map(p -> String.valueOf(p.value())).findFirst();
    }

    private static boolean booleanProperty(List<TiledProperty> properties, String name, boolean defaultValue) {
        if (properties == null) {
            return defaultValue;
        }
        return properties.stream().filter(p -> p.name().equals(name)).map(p -> (Boolean) p.value()).findFirst()
                .orElse(defaultValue);
    }

    private static double doubleProperty(List<TiledProperty> properties, String name) {
        return properties.stream().filter(p -> p.name().equals(name)).map(p -> ((Number) p.value()).doubleValue())
                .findFirst().orElseThrow(() -> new IllegalStateException("Propriété Tiled \"" + name + "\" manquante"));
    }

    private static double doubleProperty(List<TiledProperty> properties, String name, double defaultValue) {
        if (properties == null) {
            return defaultValue;
        }
        return properties.stream().filter(p -> p.name().equals(name)).map(p -> ((Number) p.value()).doubleValue())
                .findFirst().orElse(defaultValue);
    }
}
