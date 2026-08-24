package fr.idev.mudserver.domain.map;

import fr.idev.mudserver.domain.world.CollisionGrid;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;

public final class GridPathfinder {

    private static final int[][] NEIGHBOR_OFFSETS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1},
            {-1, -1},};
    private static final double DIAGONAL_COST = Math.sqrt(2);

    private GridPathfinder() {
    }

    public static Optional<List<Position>> findPath(Position start, Position target, CollisionGrid grid) {
        GridCell startCell = grid.cellOf(start);
        GridCell targetCell = grid.cellOf(target);
        if (!grid.isWalkableCell(targetCell.col(), targetCell.row())) {
            return Optional.empty();
        }
        if (startCell.equals(targetCell)) {
            return Optional.of(List.of(target));
        }

        List<GridCell> cellPath = findCellPath(startCell, targetCell, grid);
        if (cellPath == null) {
            return Optional.empty();
        }

        List<Position> full = new ArrayList<>();
        full.add(start);
        for (GridCell cell : cellPath.subList(1, cellPath.size())) {
            full.add(grid.cellCenter(cell));
        }
        full.set(full.size() - 1, target);

        List<Position> smoothed = smooth(full, grid);
        return Optional.of(smoothed.subList(1, smoothed.size()));
    }

    private static List<GridCell> findCellPath(GridCell start, GridCell target, CollisionGrid grid) {
        Map<GridCell, GridCell> cameFrom = new HashMap<>();
        Map<GridCell, Double> costSoFar = new HashMap<>();
        Set<GridCell> closed = new HashSet<>();
        PriorityQueue<Node> frontier = new PriorityQueue<>(Comparator.comparingDouble(Node::priority));
        costSoFar.put(start, 0.0);
        frontier.add(new Node(start, octileHeuristic(start, target)));

        while (!frontier.isEmpty()) {
            GridCell current = frontier.poll().cell();
            if (!closed.add(current)) {
                continue;
            }
            if (current.equals(target)) {
                return reconstruct(cameFrom, target);
            }
            for (int[] offset : NEIGHBOR_OFFSETS) {
                int col = current.col() + offset[0];
                int row = current.row() + offset[1];
                if (!grid.isWalkableCell(col, row)) {
                    continue;
                }
                boolean diagonal = offset[0] != 0 && offset[1] != 0;
                if (diagonal && (!grid.isWalkableCell(current.col() + offset[0], current.row())
                        || !grid.isWalkableCell(current.col(), current.row() + offset[1]))) {
                    continue;
                }
                GridCell next = new GridCell(col, row);
                double newCost = costSoFar.get(current) + (diagonal ? DIAGONAL_COST : 1.0);
                if (newCost < costSoFar.getOrDefault(next, Double.MAX_VALUE)) {
                    costSoFar.put(next, newCost);
                    cameFrom.put(next, current);
                    frontier.add(new Node(next, newCost + octileHeuristic(next, target)));
                }
            }
        }
        return null;
    }

    private static List<GridCell> reconstruct(Map<GridCell, GridCell> cameFrom, GridCell target) {
        LinkedList<GridCell> path = new LinkedList<>();
        for (GridCell cursor = target; cursor != null; cursor = cameFrom.get(cursor)) {
            path.addFirst(cursor);
        }
        return path;
    }

    private static double octileHeuristic(GridCell a, GridCell b) {
        int dx = Math.abs(a.col() - b.col());
        int dy = Math.abs(a.row() - b.row());
        return Math.max(dx, dy) + (DIAGONAL_COST - 1) * Math.min(dx, dy);
    }

    private static List<Position> smooth(List<Position> waypoints, CollisionGrid grid) {
        List<Position> kept = new ArrayList<>();
        kept.add(waypoints.get(0));
        int i = 0;
        while (i < waypoints.size() - 1) {
            int j = waypoints.size() - 1;
            while (j > i + 1 && !hasLineOfSight(waypoints.get(i), waypoints.get(j), grid)) {
                j--;
            }
            kept.add(waypoints.get(j));
            i = j;
        }
        return kept;
    }

    private static boolean hasLineOfSight(Position a, Position b, CollisionGrid grid) {
        double distance = a.distanceTo(b);
        if (distance == 0) {
            return true;
        }
        double stepSize = grid.cellSize() / 4.0;
        int steps = (int) Math.ceil(distance / stepSize);
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            Position sample = new Position(a.x() + (b.x() - a.x()) * t, a.y() + (b.y() - a.y()) * t);
            if (!grid.isWalkable(sample)) {
                return false;
            }
        }
        return true;
    }

    private record Node(GridCell cell, double priority) {
    }
}
