package fr.idev.mudserver.domain.world;

import fr.idev.mudserver.domain.MonsterSpawn;
import fr.idev.mudserver.domain.NpcSpawn;
import fr.idev.mudserver.domain.map.Position;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class ZoneTemplate {

    private final UUID id;
    private final String name;
    private final String description;
    private final Boolean isStartingZone;
    private final CollisionGrid collisionGrid;
    private final Position spawnPosition;
    private final List<MonsterSpawn> monsterSpawns;
    private final List<NpcSpawn> npcSpawns;
    private List<ZoneTemplatePortal> portals = List.of();

    public ZoneTemplate(UUID id, String name, String description, Boolean isStartingZone, CollisionGrid collisionGrid,
            Position spawnPosition, List<MonsterSpawn> monsterSpawns, List<NpcSpawn> npcSpawns) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.isStartingZone = isStartingZone;
        this.collisionGrid = collisionGrid;
        this.spawnPosition = spawnPosition;
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

    public CollisionGrid getCollisionGrid() {
        return collisionGrid;
    }

    public Position getSpawnPosition() {
        return spawnPosition;
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

    public boolean containsPosition(Position position) {
        return collisionGrid.containsPosition(position);
    }

    public boolean isWalkable(Position position) {
        return collisionGrid.isWalkable(position);
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
        return "ZoneTemplate[id=" + id + ", name=" + name + ", isStartingZone=" + isStartingZone + ", spawnPosition="
                + spawnPosition + "]";
    }
}
