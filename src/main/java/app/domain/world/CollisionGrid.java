package app.domain.world;

import app.domain.map.GridCell;
import app.domain.map.Position;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

public final class CollisionGrid {

    private final int width;
    private final int height;
    private final double cellSize;
    private final BitSet walkable;

    public CollisionGrid(int width, int height, double cellSize, BitSet walkable) {
        this.width = width;
        this.height = height;
        this.cellSize = cellSize;
        this.walkable = walkable;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public double cellSize() {
        return cellSize;
    }

    public GridCell cellOf(Position position) {
        return new GridCell((int) Math.floor(position.x() / cellSize), (int) Math.floor(position.y() / cellSize));
    }

    public Position cellCenter(GridCell cell) {
        return new Position((cell.col() + 0.5) * cellSize, (cell.row() + 0.5) * cellSize);
    }

    public boolean containsCell(int col, int row) {
        return col >= 0 && col < width && row >= 0 && row < height;
    }

    public boolean containsPosition(Position position) {
        GridCell cell = cellOf(position);
        return containsCell(cell.col(), cell.row());
    }

    public boolean isWalkableCell(int col, int row) {
        if (!containsCell(col, row)) {
            return false;
        }
        return walkable.get(row * width + col);
    }

    public boolean isWalkable(Position position) {
        GridCell cell = cellOf(position);
        return isWalkableCell(cell.col(), cell.row());
    }

    public List<String> toWalkableRows() {
        List<String> rows = new ArrayList<>(height);
        for (int row = 0; row < height; row++) {
            StringBuilder rowBits = new StringBuilder(width);
            for (int col = 0; col < width; col++) {
                rowBits.append(isWalkableCell(col, row) ? '1' : '0');
            }
            rows.add(rowBits.toString());
        }
        return rows;
    }
}
