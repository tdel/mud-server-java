package fr.idev.mudserver.domain.world;

import fr.idev.mudserver.domain.MonsterSpawn;
import fr.idev.mudserver.domain.map.HexCoordinate;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class RoomTemplate {

    public static final int DEFAULT_WIDTH = 16;
    public static final int DEFAULT_HEIGHT = 8;

    private final UUID id;
    private final String name;
    private final String description;
    private final Boolean isStartingRoom;
    private final int width;
    private final int height;
    private final HexCoordinate spawnCell;
    private final List<MonsterSpawn> monsterSpawns;
    private List<RoomTemplatePortal> portals = List.of();

    public RoomTemplate(UUID id, String name, String description, Boolean isStartingRoom, int width, int height,
            HexCoordinate spawnCell, List<MonsterSpawn> monsterSpawns) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.isStartingRoom = isStartingRoom;
        this.width = width;
        this.height = height;
        this.spawnCell = spawnCell;
        this.monsterSpawns = List.copyOf(monsterSpawns);
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

    public Boolean isStartingRoom() {
        return isStartingRoom;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public HexCoordinate getSpawnCell() {
        return spawnCell;
    }

    public List<MonsterSpawn> getMonsterSpawns() {
        return monsterSpawns;
    }

    public List<RoomTemplatePortal> getPortals() {
        return portals;
    }

    public void setPortals(List<RoomTemplatePortal> portals) {
        this.portals = List.copyOf(portals);
    }

    public boolean isInBounds(HexCoordinate cell) {
        return cell.q() >= 0 && cell.q() < width && cell.r() >= 0 && cell.r() < height;
    }

    public boolean isBorderCell(HexCoordinate cell) {
        return isInBounds(cell) && (cell.q() == 0 || cell.q() == width - 1 || cell.r() == 0 || cell.r() == height - 1);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof RoomTemplate other)) {
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
        return "RoomTemplate[id=" + id + ", name=" + name + ", isStartingRoom=" + isStartingRoom + ", width=" + width
                + ", height=" + height + ", spawnCell=" + spawnCell + "]";
    }
}
