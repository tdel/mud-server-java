package fr.idev.mudserver.domain;

import java.util.ArrayList;
import java.util.List;

public record HexCoordinate(int q, int r) {

    public HexCoordinate neighbor(HexDirection direction) {
        return new HexCoordinate(q + direction.dq(), r + direction.dr());
    }

    public int distanceTo(HexCoordinate other) {
        int dq = q - other.q;
        int dr = r - other.r;
        return (Math.abs(dq) + Math.abs(dq + dr) + Math.abs(dr)) / 2;
    }

    public List<HexCoordinate> withinRadius(int radius) {
        List<HexCoordinate> cells = new ArrayList<>();
        for (int dq = -radius; dq <= radius; dq++) {
            int rMin = Math.max(-radius, -dq - radius);
            int rMax = Math.min(radius, -dq + radius);
            for (int dr = rMin; dr <= rMax; dr++) {
                cells.add(new HexCoordinate(q + dq, r + dr));
            }
        }
        return cells;
    }
}
