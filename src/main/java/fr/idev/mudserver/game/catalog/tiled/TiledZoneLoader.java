package fr.idev.mudserver.game.catalog.tiled;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import fr.idev.mudserver.domain.MonsterSpawn;
import fr.idev.mudserver.domain.map.HexCoordinate;
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
 * Map Editor, orientation hexagonale) en données de zone exploitables par
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

    public record ParsedZone(UUID id, String name, String description, boolean isStartingZone,
            Map<HexCoordinate, TileType> terrain, HexCoordinate spawnCell, List<MonsterSpawn> monsterSpawns,
            List<PortalDraft> portals) {
    }

    public record PortalDraft(HexCoordinate cell, String direction, UUID targetZoneId, HexCoordinate targetCell) {
    }

    public static ParsedZone parse(TiledMap map) {
        UUID id = UUID.fromString(requireStringProperty(map.properties(), "id"));
        String name = requireStringProperty(map.properties(), "name");
        String description = requireStringProperty(map.properties(), "description");
        boolean isStartingZone = booleanProperty(map.properties(), "isStartingZone", false);

        Map<Integer, TileType> tileTypeByGid = buildTileTypeByGid(map);
        Map<HexCoordinate, TileType> terrain = parseTerrain(map, tileTypeByGid, id);

        HexCoordinate[] spawnCellHolder = new HexCoordinate[1];
        List<MonsterSpawn> monsterSpawns = new ArrayList<>();
        List<PortalDraft> portals = new ArrayList<>();
        parseObjects(map, id, spawnCellHolder, monsterSpawns, portals);

        if (spawnCellHolder[0] == null) {
            throw new IllegalStateException("Zone " + id + " (" + name + ") n'a aucun objet playerSpawn");
        }

        return new ParsedZone(id, name, description, isStartingZone, terrain, spawnCellHolder[0],
                List.copyOf(monsterSpawns), List.copyOf(portals));
    }

    private static Map<Integer, TileType> buildTileTypeByGid(TiledMap map) {
        Map<Integer, TileType> byGid = new HashMap<>();
        for (TiledTileset tileset : map.tilesets()) {
            if (tileset.tiles() == null) {
                continue;
            }
            for (TiledTile tile : tileset.tiles()) {
                boolean walkable = booleanProperty(tile.properties(), "walkable", true);
                byGid.put(tileset.firstgid() + tile.id(), walkable ? TileType.FLOOR : TileType.WALL);
            }
        }
        return byGid;
    }

    private static Map<HexCoordinate, TileType> parseTerrain(TiledMap map, Map<Integer, TileType> tileTypeByGid,
            UUID id) {
        TiledLayer layer = findLayer(map, id, TERRAIN_LAYER, "tilelayer");
        Map<HexCoordinate, TileType> terrain = new HashMap<>();
        for (int row = 0; row < layer.height(); row++) {
            for (int col = 0; col < layer.width(); col++) {
                int gid = layer.data().get(row * layer.width() + col);
                if (gid == 0) {
                    continue;
                }
                TileType tileType = tileTypeByGid.getOrDefault(gid, TileType.FLOOR);
                terrain.put(HexTiledCoordinateMapper.offsetToAxial(col, row), tileType);
            }
        }
        return terrain;
    }

    private static void parseObjects(TiledMap map, UUID id, HexCoordinate[] spawnCellHolder,
            List<MonsterSpawn> monsterSpawns, List<PortalDraft> portals) {
        TiledLayer layer = findLayer(map, id, OBJECTS_LAYER, "objectgroup");
        for (TiledObjectDef object : layer.objects()) {
            HexCoordinate cell = HexTiledCoordinateMapper.pixelToAxial(object.x(), object.y(), map.tilewidth(),
                    map.tileheight(), map.hexsidelength());
            switch (object.type()) {
                case TYPE_PLAYER_SPAWN -> spawnCellHolder[0] = cell;
                case TYPE_PORTAL ->
                    portals.add(new PortalDraft(cell, requireStringProperty(object.properties(), "direction"),
                            UUID.fromString(requireStringProperty(object.properties(), "targetZoneId")),
                            new HexCoordinate(intProperty(object.properties(), "targetCellQ"),
                                    intProperty(object.properties(), "targetCellR"))));
                case TYPE_MONSTER_SPAWN -> monsterSpawns.add(new MonsterSpawn(
                        UUID.nameUUIDFromBytes((id + ":" + object.id()).getBytes(StandardCharsets.UTF_8)),
                        UUID.fromString(requireStringProperty(object.properties(), "templateId")), cell));
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

    private static int intProperty(List<TiledProperty> properties, String name) {
        return properties.stream().filter(p -> p.name().equals(name)).map(p -> ((Number) p.value()).intValue())
                .findFirst().orElseThrow(() -> new IllegalStateException("Propriété Tiled \"" + name + "\" manquante"));
    }
}
