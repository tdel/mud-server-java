package fr.idev.mudserver.domain.map;

import static org.assertj.core.api.Assertions.assertThat;

import fr.idev.mudserver.domain.world.CollisionGrid;
import java.util.BitSet;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class GridPathfinderTest {

    private CollisionGrid gridOf(String... rows) {
        int height = rows.length;
        int width = rows[0].length();
        BitSet walkable = new BitSet(width * height);
        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                walkable.set(row * width + col, rows[row].charAt(col) == '.');
            }
        }
        return new CollisionGrid(width, height, 1.0, walkable);
    }

    @Test
    void findsDirectPathWhenNoObstacle() {
        CollisionGrid grid = gridOf(".....", ".....", ".....");

        Optional<List<Position>> path = GridPathfinder.findPath(new Position(0.5, 0.5), new Position(4.5, 0.5), grid);

        assertThat(path).isPresent();
        assertThat(path.get()).isNotEmpty();
        assertThat(path.get().getLast()).isEqualTo(new Position(4.5, 0.5));
    }

    @Test
    void goesAroundAWall() {
        CollisionGrid grid = gridOf(".....", ".###.", ".....");

        Optional<List<Position>> path = GridPathfinder.findPath(new Position(0.5, 1.5), new Position(4.5, 1.5), grid);

        assertThat(path).isPresent();
        assertThat(path.get().getLast()).isEqualTo(new Position(4.5, 1.5));
    }

    @Test
    void returnsEmptyWhenTargetIsUnreachable() {
        CollisionGrid grid = gridOf(".#.", "###", ".#.");

        Optional<List<Position>> path = GridPathfinder.findPath(new Position(0.5, 0.5), new Position(2.5, 2.5), grid);

        assertThat(path).isEmpty();
    }

    @Test
    void doesNotCutCornersDiagonally() {
        CollisionGrid grid = gridOf(".#", "#.");

        Optional<List<Position>> path = GridPathfinder.findPath(new Position(0.5, 0.5), new Position(1.5, 1.5), grid);

        assertThat(path).isEmpty();
    }

    @Test
    void smoothsLShapedCorridorIntoFewWaypoints() {
        CollisionGrid grid = gridOf("......", "#####.", "#####.", "......");

        Optional<List<Position>> path = GridPathfinder.findPath(new Position(0.5, 0.5), new Position(0.5, 3.5), grid);

        assertThat(path).isPresent();
        assertThat(path.get().size()).isLessThanOrEqualTo(3);
    }
}
