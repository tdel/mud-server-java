package fr.idev.mudserver.domain.world;

import fr.idev.mudserver.domain.MonsterSpawn;
import fr.idev.mudserver.domain.NpcSpawn;
import fr.idev.mudserver.domain.map.HexCoordinate;

import java.util.Map;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class ZoneTemplate {

    private final UUID id;
    private final String name;
    private final String description;
    private final Boolean isStartingZone;
    private final Map<HexCoordinate, TileType> terrain;
    private final HexCoordinate spawnCell;
    private final List<MonsterSpawn> monsterSpawns;
    private final List<NpcSpawn> npcSpawns;
    private List<ZoneTemplatePortal> portals = List.of();

    public ZoneTemplate(UUID id, String name, String description, Boolean isStartingZone,
            Map<HexCoordinate, TileType> terrain, HexCoordinate spawnCell, List<MonsterSpawn> monsterSpawns,
            List<NpcSpawn> npcSpawns) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.isStartingZone = isStartingZone;
        this.terrain = Map.copyOf(terrain);
        this.spawnCell = spawnCell;
        this.monsterSpawns = List.copyOf(monsterSpawns);
        this.npcSpawns = List.copyOf(npcSpawns);
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Boolean isStartingZone() {
        return isStartingZone;
    }

    public Map<HexCoordinate, TileType> getTerrain() {
        return terrain;
    }

    public HexCoordinate getSpawnCell() {
        return spawnCell;
    }

    public List<MonsterSpawn> getMonsterSpawns() {
        return monsterSpawns;
    }

    public List<NpcSpawn> getNpcSpawns() {
        return npcSpawns;
    }

    public List<ZoneTemplatePortal> getPortals() {
        return portals;
    }

    public void setPortals(List<ZoneTemplatePortal> portals) {
        this.portals = List.copyOf(portals);
    }

    public boolean containsCell(HexCoordinate cell) {
        return terrain.containsKey(cell);
    }

    public boolean isWalkable(HexCoordinate cell) {
        TileType tile = terrain.get(cell);
        return tile != null && tile.isWalkable();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ZoneTemplate other)) {
            return false;
        }
        return Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "ZoneTemplate[id=" + id + ", name=" + name + ", isStartingZone=" + isStartingZone + ", cells="
                + terrain.size() + ", spawnCell=" + spawnCell + "]";
    }
}
