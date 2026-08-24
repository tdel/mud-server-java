package fr.idev.mudserver.domain.world;

import static org.assertj.core.api.Assertions.assertThat;

import fr.idev.mudserver.domain.map.GridCell;
import fr.idev.mudserver.domain.map.Position;
import java.util.BitSet;
import org.junit.jupiter.api.Test;

class CollisionGridTest {

    private CollisionGrid openGrid() {
        int width = 4;
        int height = 4;
        BitSet walkable = new BitSet(width * height);
        walkable.set(0, width * height, true);
        return new CollisionGrid(width, height, 1.0, walkable);
    }

    private CollisionGrid gridWithWallAt(int wallCol, int wallRow) {
        int width = 4;
        int height = 4;
        BitSet walkable = new BitSet(width * height);
        walkable.set(0, width * height, true);
        walkable.clear(wallRow * width + wallCol);
        return new CollisionGrid(width, height, 1.0, walkable);
    }

    @Test
    void cellOfFloorsToContainingCell() {
        CollisionGrid grid = openGrid();

        assertThat(grid.cellOf(new Position(1.9, 0.1))).isEqualTo(new GridCell(1, 0));
    }

    @Test
    void cellOfHandlesNegativePositions() {
        CollisionGrid grid = openGrid();

        assertThat(grid.cellOf(new Position(-0.5, -0.5))).isEqualTo(new GridCell(-1, -1));
    }

    @Test
    void isWalkableIsFalseOutsideGridBounds() {
        CollisionGrid grid = openGrid();

        assertThat(grid.isWalkable(new Position(-1, -1))).isFalse();
        assertThat(grid.isWalkable(new Position(100, 100))).isFalse();
    }

    @Test
    void isWalkableReflectsWallCell() {
        CollisionGrid grid = gridWithWallAt(2, 1);

        assertThat(grid.isWalkable(new Position(2.5, 1.5))).isFalse();
        assertThat(grid.isWalkable(new Position(0.5, 0.5))).isTrue();
    }

    @Test
    void cellCenterReturnsMiddleOfCell() {
        CollisionGrid grid = openGrid();

        assertThat(grid.cellCenter(new GridCell(2, 1))).isEqualTo(new Position(2.5, 1.5));
    }
}
