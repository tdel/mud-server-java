package fr.idev.mudserver.domain.map;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

public final class HexPathfinder {

    private HexPathfinder() {
    }

    public static Optional<List<HexCoordinate>> findPath(HexCoordinate start, HexCoordinate target,
            Predicate<HexCoordinate> isWalkable) {
        if (start.equals(target)) {
            return Optional.of(List.of());
        }

        Map<HexCoordinate, HexCoordinate> cameFrom = new HashMap<>();
        Deque<HexCoordinate> queue = new ArrayDeque<>();
        cameFrom.put(start, null);
        queue.add(start);

        while (!queue.isEmpty()) {
            HexCoordinate current = queue.poll();
            for (HexDirection direction : HexDirection.values()) {
                HexCoordinate next = current.neighbor(direction);
                if (cameFrom.containsKey(next) || !isWalkable.test(next)) {
                    continue;
                }
                cameFrom.put(next, current);
                if (next.equals(target)) {
                    return Optional.of(reconstruct(cameFrom, target));
                }
                queue.add(next);
            }
        }
        return Optional.empty();
    }

    private static List<HexCoordinate> reconstruct(Map<HexCoordinate, HexCoordinate> cameFrom, HexCoordinate target) {
        LinkedList<HexCoordinate> path = new LinkedList<>();
        for (HexCoordinate cursor = target; cursor != null; cursor = cameFrom.get(cursor)) {
            path.addFirst(cursor);
        }
        path.removeFirst();
        return path;
    }
}
