package fr.idev.mudserver.game.catalog.tiled;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import fr.idev.mudserver.domain.MonsterSpawn;
import fr.idev.mudserver.domain.map.HexCoordinate;
import fr.idev.mudserver.domain.world.TileType;
import fr.idev.mudserver.game.catalog.tiled.TiledMap.TiledLayer;
import fr.idev.mudserver.game.catalog.tiled.TiledMap.TiledObjectDef;
import fr.idev.mudserver.game.catalog.tiled.TiledMap.TiledProperty;
import fr.idev.mudserver.game.catalog.tiled.TiledMap.TiledTile;
import fr.idev.mudserver.game.catalog.tiled.TiledMap.TiledTileset;
import fr.idev.mudserver.game.catalog.tiled.TiledZoneLoader.ParsedZone;
import org.junit.jupiter.api.Test;

class TiledZoneLoaderTest {

    private static final int TILE_WIDTH = 32;
    private static final int TILE_HEIGHT = 28;
    private static final int HEX_SIDE_LENGTH = 8;

    @Test
    void parsesTerrainPortalsAndSpawnsFromATiledMap() {
        UUID zoneId = UUID.randomUUID();
        UUID targetZoneId = UUID.randomUUID();
        UUID monsterTemplateId = UUID.randomUUID();

        TiledTileset tileset = new TiledTileset(1,
                List.of(new TiledTile(0, List.of(new TiledProperty("walkable", "bool", true))),
                        new TiledTile(1, List.of(new TiledProperty("walkable", "bool", false)))));

        // grille 2x2 : (0,0) et (1,0) sol, (0,1) sol, (1,1) mur.
        TiledLayer terrain = new TiledLayer("tilelayer", "terrain", 2, 2, List.of(1, 1, 1, 2), null, null);

        TiledObjectDef spawnObject = objectAt("playerSpawn", 0, 0, List.of());
        TiledObjectDef portalObject = objectAt("portal", 1, 0,
                List.of(new TiledProperty("direction", "string", "E"),
                        new TiledProperty("targetZoneId", "string", targetZoneId.toString()),
                        new TiledProperty("targetCellQ", "int", 0), new TiledProperty("targetCellR", "int", 0)));
        TiledObjectDef monsterSpawnObject = objectAt("monsterSpawn", 0, 1,
                List.of(new TiledProperty("templateId", "string", monsterTemplateId.toString())));

        TiledLayer objects = new TiledLayer("objectgroup", "objects", null, null, null,
                List.of(spawnObject, portalObject, monsterSpawnObject), null);

        TiledMap map = new TiledMap("hexagonal", 2, 2, TILE_WIDTH, TILE_HEIGHT, HEX_SIDE_LENGTH, "y", "odd",
                List.of(terrain, objects), List.of(tileset),
                List.of(new TiledProperty("id", "string", zoneId.toString()),
                        new TiledProperty("name", "string", "Zone de test"),
                        new TiledProperty("description", "string", "Une zone pour les tests"),
                        new TiledProperty("isStartingZone", "bool", true)));

        ParsedZone parsed = TiledZoneLoader.parse(map);

        assertThat(parsed.id()).isEqualTo(zoneId);
        assertThat(parsed.name()).isEqualTo("Zone de test");
        assertThat(parsed.isStartingZone()).isTrue();
        assertThat(parsed.spawnCell()).isEqualTo(HexTiledCoordinateMapper.offsetToAxial(0, 0));

        HexCoordinate wallCell = HexTiledCoordinateMapper.offsetToAxial(1, 1);
        assertThat(parsed.terrain()).hasSize(4);
        assertThat(parsed.terrain().get(wallCell)).isEqualTo(TileType.WALL);
        assertThat(parsed.terrain().get(HexTiledCoordinateMapper.offsetToAxial(0, 0))).isEqualTo(TileType.FLOOR);

        assertThat(parsed.portals()).hasSize(1);
        var portal = parsed.portals().get(0);
        assertThat(portal.cell()).isEqualTo(HexTiledCoordinateMapper.offsetToAxial(1, 0));
        assertThat(portal.direction()).isEqualTo("E");
        assertThat(portal.targetZoneId()).isEqualTo(targetZoneId);
        assertThat(portal.targetCell()).isEqualTo(new HexCoordinate(0, 0));

        assertThat(parsed.monsterSpawns()).hasSize(1);
        MonsterSpawn spawn = parsed.monsterSpawns().get(0);
        assertThat(spawn.templateId()).isEqualTo(monsterTemplateId);
        assertThat(spawn.cell()).isEqualTo(HexTiledCoordinateMapper.offsetToAxial(0, 1));
    }

    private static TiledObjectDef objectAt(String type, int col, int row, List<TiledProperty> properties) {
        double[] center = HexTiledCoordinateMapper.cellCenterPixel(col, row, TILE_WIDTH, TILE_HEIGHT, HEX_SIDE_LENGTH);
        return new TiledObjectDef(col * 10 + row, type, type, center[0], center[1], properties);
    }
}
