package app.domain.world;

import app.domain.MonsterSpawn;
import app.domain.MonsterSpawnGroup;
import app.domain.NpcSpawn;
import app.domain.map.GridCell;
import app.domain.map.Position;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class MapTemplate {

    // Arrivée d'un Scroll of Escape : case au hasard à moins de ce rayon (en
    // cases) du playerSpawn — le cœur de la ville (la Place du village a ses
    // remparts à ~26 cases du spawn), jamais dans la campagne alentour.
    private static final int TOWN_SCATTER_RADIUS_CELLS = 20;
    // Même garde que TiledMapLoader.MIN_SPAWN_PORTAL_DISTANCE : ne jamais
    // arriver sur (ou tout contre) un téléporteur.
    private static final int TOWN_SCATTER_PORTAL_CLEARANCE_CELLS = 5;

    private final UUID id;
    private final String name;
    private final String description;
    private final Boolean isStartingMap;
    private final boolean isTown;
    private final CollisionGrid collisionGrid;
    private final Position spawnPosition;
    private final List<MonsterSpawn> monsterSpawns;
    private final List<MonsterSpawnGroup> monsterSpawnGroups;
    private final List<NpcSpawn> npcSpawns;
    private List<MapTemplatePortal> portals = List.of();
    private List<PeaceZone> peaceZones = List.of();
    // Calculé paresseusement (cf. townArrivalCells) : les portails ne sont connus
    // qu'après setPortals.
    private volatile List<GridCell> townArrivalCells;

    public MapTemplate(UUID id, String name, String description, Boolean isStartingMap, boolean isTown,
            CollisionGrid collisionGrid, Position spawnPosition, List<MonsterSpawn> monsterSpawns,
            List<MonsterSpawnGroup> monsterSpawnGroups, List<NpcSpawn> npcSpawns) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.isStartingMap = isStartingMap;
        this.isTown = isTown;
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

    public boolean isTown() {
        return isTown;
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
        this.townArrivalCells = null;
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

    // Point d'arrivée au hasard dans la ville (Scroll of Escape) : centre d'une
    // case praticable reliée au playerSpawn (jamais une poche enclavée entre
    // deux maisons), proche du spawn et loin des portails. Repli sur le spawn
    // si aucune case ne convient.
    public Position randomTownPosition() {
        List<GridCell> cells = townArrivalCells();
        if (cells.isEmpty()) {
            return spawnPosition;
        }
        return collisionGrid.cellCenter(cells.get(ThreadLocalRandom.current().nextInt(cells.size())));
    }

    private List<GridCell> townArrivalCells() {
        List<GridCell> cells = townArrivalCells;
        if (cells == null) {
            cells = computeTownArrivalCells();
            townArrivalCells = cells;
        }
        return cells;
    }

    // Parcours en largeur (4-voisinage) depuis la case du playerSpawn, borné au
    // rayon TOWN_SCATTER_RADIUS_CELLS.
    private List<GridCell> computeTownArrivalCells() {
        GridCell start = collisionGrid.cellOf(spawnPosition);
        if (!collisionGrid.isWalkableCell(start.col(), start.row())) {
            return List.of();
        }
        int width = collisionGrid.width();
        BitSet visited = new BitSet(width * collisionGrid.height());
        Deque<GridCell> queue = new ArrayDeque<>();
        List<GridCell> result = new ArrayList<>();
        double portalClearance = TOWN_SCATTER_PORTAL_CLEARANCE_CELLS * collisionGrid.cellSize();
        queue.add(start);
        visited.set(start.row() * width + start.col());
        int[][] steps = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!queue.isEmpty()) {
            GridCell cell = queue.poll();
            Position center = collisionGrid.cellCenter(cell);
            boolean nearPortal = portals.stream()
                    .anyMatch(portal -> portal.position().distanceTo(center) < portalClearance);
            if (!nearPortal) {
                result.add(cell);
            }
            for (int[] step : steps) {
                int col = cell.col() + step[0];
                int row = cell.row() + step[1];
                if (!collisionGrid.isWalkableCell(col, row) || visited.get(row * width + col)) {
                    continue;
                }
                int dc = col - start.col();
                int dr = row - start.row();
                if (dc * dc + dr * dr > TOWN_SCATTER_RADIUS_CELLS * TOWN_SCATTER_RADIUS_CELLS) {
                    continue;
                }
                visited.set(row * width + col);
                queue.add(new GridCell(col, row));
            }
        }
        return List.copyOf(result);
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
        return "MapTemplate[id=" + id + ", name=" + name + ", isStartingMap=" + isStartingMap + ", isTown=" + isTown
                + ", spawnPosition=" + spawnPosition + "]";
    }
}
