package app.domain.world;

import app.domain.MonsterSpawn;
import app.domain.MonsterSpawnGroup;
import app.domain.NpcSpawn;
import app.domain.map.Position;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class MapTemplate {

    private final UUID id;
    private final String name;
    private final String description;
    private final Boolean isStartingMap;
    private final CollisionGrid collisionGrid;
    private final Position spawnPosition;
    private final List<MonsterSpawn> monsterSpawns;
    private final List<MonsterSpawnGroup> monsterSpawnGroups;
    private final List<NpcSpawn> npcSpawns;
    private List<MapTemplatePortal> portals = List.of();
    private List<PeaceZone> peaceZones = List.of();

    public MapTemplate(UUID id, String name, String description, Boolean isStartingMap, CollisionGrid collisionGrid,
            Position spawnPosition, List<MonsterSpawn> monsterSpawns, List<MonsterSpawnGroup> monsterSpawnGroups,
            List<NpcSpawn> npcSpawns) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.isStartingMap = isStartingMap;
        this.collisionGrid = collisionGrid;
        this.spawnPosition = spawnPosition;
        this.monsterSpawns = List.copyOf(monsterSpawns);
        this.monsterSpawnGroups = List.copyOf(monsterSpawnGroups);
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

    public Boolean isStartingMap() {
        return isStartingMap;
    }

    public CollisionGrid getCollisionGrid() {
        return collisionGrid;
    }

    public Position getSpawnPosition() {
        return spawnPosition;
    }

    public List<MonsterSpawn> getMonsterSpawns() {
        return monsterSpawns;
    }

    public List<MonsterSpawnGroup> getMonsterSpawnGroups() {
        return monsterSpawnGroups;
    }

    public List<NpcSpawn> getNpcSpawns() {
        return npcSpawns;
    }

    public List<MapTemplatePortal> getPortals() {
        return portals;
    }

    public void setPortals(List<MapTemplatePortal> portals) {
        this.portals = List.copyOf(portals);
    }

    public void setPeaceZones(List<PeaceZone> peaceZones) {
        this.peaceZones = List.copyOf(peaceZones);
    }

    public boolean containsPosition(Position position) {
        return collisionGrid.containsPosition(position);
    }

    public boolean isWalkable(Position position) {
        return collisionGrid.isWalkable(position);
    }

    public AbstractZone zoneAt(Position position) {
        for (PeaceZone zone : peaceZones) {
            if (zone.contains(position)) {
                return zone;
            }
        }
        return NormalZone.INSTANCE;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof MapTemplate other)) {
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
        return "MapTemplate[id=" + id + ", name=" + name + ", isStartingMap=" + isStartingMap + ", spawnPosition="
                + spawnPosition + "]";
    }
}
