package app.game.catalog.tiled;

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

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import app.domain.MonsterSpawn;
import app.domain.MonsterSpawnGroup;
import app.domain.NpcSpawn;
import app.domain.map.Position;
import app.domain.world.CollisionGrid;
import app.domain.world.TileType;
import app.game.catalog.tiled.TiledMap.TiledLayer;
import app.game.catalog.tiled.TiledMap.TiledObjectDef;
import app.game.catalog.tiled.TiledMap.TiledProperty;
import app.game.catalog.tiled.TiledMap.TiledTile;
import app.game.catalog.tiled.TiledMap.TiledTileset;

/**
 * Convertit un fichier {@code .tmx} (XML natif Tiled Map Editor, orientation
 * orthogonale) en données de zone exploitables par {@code WorldTemplateCatalog}.
 * Un fichier par zone : un calque de tuiles nommé "terrain" porte le terrain
 * praticable/bloqué (propriété custom booléenne "walkable" sur chaque tuile du
 * tileset), un calque d'objets nommé "objects" porte le point de spawn du
 * joueur, les portails et les spawns de monstres. Le {@code .tmx} est lu tel
 * quel : c'est à la fois le fichier ouvert par Tiled Map Editor et la seule
 * source lue par le serveur, il n'y a pas d'export JSON intermédiaire.
 */
public final class TiledZoneLoader {

    private static final String TERRAIN_LAYER = "terrain";
    private static final String OBJECTS_LAYER = "objects";
    private static final String TYPE_PLAYER_SPAWN = "playerSpawn";
    private static final String TYPE_PORTAL = "portal";
    private static final String TYPE_MONSTER_SPAWN = "monsterSpawn";
    private static final String TYPE_MONSTER_SPAWN_GROUP = "monsterSpawnGroup";
    private static final String TYPE_NPC_SPAWN = "npcSpawn";
    // Élargi de 1.2 à 4.0 tuiles (2026-08-30, agrandissement des zones : un
    // portail doit pouvoir accueillir un groupe d'une dizaine de personnages sans
    // qu'elles se sentent entassées, ce qui suppose une zone de déclenchement
    // nettement plus large qu'un simple point visé au pixel près). Les zones
    // agrandies espacent leurs portails de bien plus que l'ancien minimum
    // (~8 tuiles) : aucun risque de chevauchement avec checkNoOverlappingPortals
    // (WorldTemplateCatalog), qui exige distance >= somme des deux rayons.
    private static final double DEFAULT_PORTAL_TRIGGER_RADIUS = 4.0;
    private static final double COLLISION_CELL_SIZE = 1.0;
    // Doit rester strictement supérieur à DEFAULT_PORTAL_TRIGGER_RADIUS : sinon un
    // joueur pourrait apparaître (spawn) déjà à l'intérieur du rayon de
    // déclenchement d'un portail et être aussitôt téléporté.
    private static final double MIN_SPAWN_PORTAL_DISTANCE = 5.0;

    private TiledZoneLoader() {
    }

    public static TiledMap readMap(InputStream in) {
        Document document = parseXml(in);
        Element mapElement = document.getDocumentElement();

        int width = Integer.parseInt(mapElement.getAttribute("width"));
        int height = Integer.parseInt(mapElement.getAttribute("height"));
        int tilewidth = Integer.parseInt(mapElement.getAttribute("tilewidth"));
        int tileheight = Integer.parseInt(mapElement.getAttribute("tileheight"));
        List<TiledProperty> properties = readProperties(directChild(mapElement, "properties"));

        List<TiledTileset> tilesets = new ArrayList<>();
        for (Element tilesetElement : directChildren(mapElement, "tileset")) {
            tilesets.add(readTileset(tilesetElement));
        }

        List<TiledLayer> layers = new ArrayList<>();
        for (Element layerElement : directChildren(mapElement, "layer")) {
            layers.add(readTileLayer(layerElement));
        }
        for (Element objectGroupElement : directChildren(mapElement, "objectgroup")) {
            layers.add(readObjectLayer(objectGroupElement));
        }

        return new TiledMap(mapElement.getAttribute("orientation"), width, height, tilewidth, tileheight, layers,
                tilesets, properties);
    }

    public record ParsedZone(UUID id, String name, String description, boolean isStartingZone, CollisionGrid terrain,
            Position spawnPosition, List<MonsterSpawn> monsterSpawns, List<MonsterSpawnGroup> monsterSpawnGroups,
            List<NpcSpawn> npcSpawns, List<PortalDraft> portals) {
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
        List<MonsterSpawnGroup> monsterSpawnGroups = new ArrayList<>();
        List<NpcSpawn> npcSpawns = new ArrayList<>();
        List<PortalDraft> portals = new ArrayList<>();
        parseObjects(map, id, spawnPositionHolder, monsterSpawns, monsterSpawnGroups, npcSpawns, portals);

        if (spawnPositionHolder[0] == null) {
            throw new IllegalStateException("Zone " + id + " (" + name + ") n'a aucun objet playerSpawn");
        }

        for (PortalDraft portal : portals) {
            double distance = portal.position().distanceTo(spawnPositionHolder[0]);
            if (distance < MIN_SPAWN_PORTAL_DISTANCE) {
                throw new IllegalStateException(
                        "Zone " + id + " (" + name + ") : le playerSpawn est trop proche (" + distance + " < "
                                + MIN_SPAWN_PORTAL_DISTANCE + ") du portail direction " + portal.direction());
            }
        }

        Map<String, MonsterSpawnGroup> groupsById = new HashMap<>();
        for (MonsterSpawnGroup group : monsterSpawnGroups) {
            if (groupsById.putIfAbsent(group.id(), group) != null) {
                throw new IllegalStateException(
                        "Zone " + id + " (" + name + ") a plusieurs monsterSpawnGroup avec groupId=" + group.id());
            }
        }
        for (MonsterSpawn spawn : monsterSpawns) {
            if (!groupsById.containsKey(spawn.groupId())) {
                throw new IllegalStateException("Zone " + id + " (" + name + ") : le monsterSpawn " + spawn.id()
                        + " référence le spawnGroup " + spawn.groupId() + ", absent des monsterSpawnGroup de la zone");
            }
        }

        return new ParsedZone(id, name, description, isStartingZone, terrain, spawnPositionHolder[0],
                List.copyOf(monsterSpawns), List.copyOf(monsterSpawnGroups), List.copyOf(npcSpawns),
                List.copyOf(portals));
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
    // Tiled XML natif (ouvrable tel quel dans Tiled Map Editor) — on n'en
    // extrait que les <tile>/<properties> utiles, le reste (image, colonnes,
    // etc.) n'étant pas exploité côté serveur.
    private static List<TiledTile> readExternalTileset(String source,
            Function<String, InputStream> externalTilesetResolver) {
        try (InputStream in = externalTilesetResolver.apply(source)) {
            Document document = parseXml(in);
            NodeList tileNodes = document.getElementsByTagName("tile");
            List<TiledTile> tiles = new ArrayList<>();
            for (int i = 0; i < tileNodes.getLength(); i++) {
                Element tileElement = (Element) tileNodes.item(i);
                int id = Integer.parseInt(tileElement.getAttribute("id"));
                tiles.add(new TiledTile(id, readProperties(directChild(tileElement, "properties"))));
            }
            return tiles;
        } catch (Exception e) {
            throw new IllegalStateException("Impossible de charger le tileset externe " + source, e);
        }
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
            List<MonsterSpawn> monsterSpawns, List<MonsterSpawnGroup> monsterSpawnGroups, List<NpcSpawn> npcSpawns,
            List<PortalDraft> portals) {
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
                        UUID.fromString(requireStringProperty(object.properties(), "templateId")), position,
                        requireStringProperty(object.properties(), "spawnGroup")));
                case TYPE_MONSTER_SPAWN_GROUP ->
                    monsterSpawnGroups.add(new MonsterSpawnGroup(requireStringProperty(object.properties(), "groupId"),
                            (int) doubleProperty(object.properties(), "maxMonsters"),
                            (long) doubleProperty(object.properties(), "respawnDelaySeconds")));
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

    // --- Parsing XML (.tmx) bas niveau ---

    private static Document parseXml(InputStream in) {
        try {
            DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            return builder.parse(in);
        } catch (Exception e) {
            throw new IllegalStateException("Impossible de lire un fichier XML Tiled", e);
        }
    }

    private static TiledTileset readTileset(Element tilesetElement) {
        int firstgid = Integer.parseInt(tilesetElement.getAttribute("firstgid"));
        boolean external = tilesetElement.hasAttribute("source");
        String source = external ? tilesetElement.getAttribute("source") : null;
        List<TiledTile> tiles = null;
        if (!external) {
            tiles = new ArrayList<>();
            for (Element tileElement : directChildren(tilesetElement, "tile")) {
                int id = Integer.parseInt(tileElement.getAttribute("id"));
                tiles.add(new TiledTile(id, readProperties(directChild(tileElement, "properties"))));
            }
        }
        return new TiledTileset(firstgid, source, tiles);
    }

    private static TiledLayer readTileLayer(Element layerElement) {
        String name = layerElement.getAttribute("name");
        int width = Integer.parseInt(layerElement.getAttribute("width"));
        int height = Integer.parseInt(layerElement.getAttribute("height"));
        Element dataElement = directChild(layerElement, "data")
                .orElseThrow(() -> new IllegalStateException("Calque \"" + name + "\" sans balise <data>"));
        String encoding = dataElement.getAttribute("encoding");
        if (!"csv".equals(encoding)) {
            throw new IllegalStateException(
                    "Calque \"" + name + "\" : encodage \"" + encoding + "\" non supporté (seul \"csv\" l'est)");
        }
        List<Integer> data = new ArrayList<>();
        for (String token : dataElement.getTextContent().split(",")) {
            String trimmed = token.trim();
            if (!trimmed.isEmpty()) {
                data.add(Integer.valueOf(trimmed));
            }
        }
        return new TiledLayer("tilelayer", name, width, height, data, null, List.of());
    }

    private static TiledLayer readObjectLayer(Element objectGroupElement) {
        String name = objectGroupElement.getAttribute("name");
        List<TiledObjectDef> objects = new ArrayList<>();
        for (Element objectElement : directChildren(objectGroupElement, "object")) {
            int id = Integer.parseInt(objectElement.getAttribute("id"));
            String objectName = objectElement.getAttribute("name");
            String type = objectElement.getAttribute("type");
            double x = Double.parseDouble(objectElement.getAttribute("x"));
            double y = Double.parseDouble(objectElement.getAttribute("y"));
            List<TiledProperty> properties = readProperties(directChild(objectElement, "properties"));
            objects.add(new TiledObjectDef(id, objectName, type, x, y, properties));
        }
        return new TiledLayer("objectgroup", name, null, null, null, objects, List.of());
    }

    private static List<TiledProperty> readProperties(Optional<Element> propertiesElement) {
        if (propertiesElement.isEmpty()) {
            return List.of();
        }
        List<TiledProperty> properties = new ArrayList<>();
        for (Element propertyElement : directChildren(propertiesElement.get(), "property")) {
            String name = propertyElement.getAttribute("name");
            String type = propertyElement.hasAttribute("type") ? propertyElement.getAttribute("type") : "string";
            String rawValue = propertyElement.hasAttribute("value")
                    ? propertyElement.getAttribute("value")
                    : propertyElement.getTextContent();
            properties.add(new TiledProperty(name, type, convertPropertyValue(type, rawValue)));
        }
        return properties;
    }

    private static Object convertPropertyValue(String type, String rawValue) {
        return switch (type) {
            case "bool" -> Boolean.valueOf(rawValue);
            case "int" -> Integer.valueOf(rawValue);
            case "float" -> Double.valueOf(rawValue);
            default -> rawValue;
        };
    }

    private static List<Element> directChildren(Element parent, String tagName) {
        List<Element> result = new ArrayList<>();
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element element && tagName.equals(element.getTagName())) {
                result.add(element);
            }
        }
        return result;
    }

    private static Optional<Element> directChild(Element parent, String tagName) {
        List<Element> children = directChildren(parent, tagName);
        return children.isEmpty() ? Optional.empty() : Optional.of(children.get(0));
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
