package fr.idev.mudserver.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HexCoordinateTest {

    private final HexCoordinate origin = new HexCoordinate(5, 5);

    @Test
    void neighborAppliesTheDeltaOfEachDirection() {
        assertThat(origin.neighbor(HexDirection.E)).isEqualTo(new HexCoordinate(6, 5));
        assertThat(origin.neighbor(HexDirection.W)).isEqualTo(new HexCoordinate(4, 5));
        assertThat(origin.neighbor(HexDirection.NE)).isEqualTo(new HexCoordinate(6, 4));
        assertThat(origin.neighbor(HexDirection.NW)).isEqualTo(new HexCoordinate(5, 4));
        assertThat(origin.neighbor(HexDirection.SE)).isEqualTo(new HexCoordinate(5, 6));
        assertThat(origin.neighbor(HexDirection.SW)).isEqualTo(new HexCoordinate(4, 6));
    }

    @Test
    void distanceToASingleStepNeighborIsOne() {
        for (HexDirection direction : HexDirection.values()) {
            assertThat(origin.distanceTo(origin.neighbor(direction))).isEqualTo(1);
        }
    }

    @Test
    void distanceToItselfIsZero() {
        assertThat(origin.distanceTo(origin)).isZero();
    }

    @Test
    void distanceToIsSymmetric() {
        HexCoordinate other = new HexCoordinate(9, 2);
        assertThat(origin.distanceTo(other)).isEqualTo(other.distanceTo(origin));
    }

    @Test
    void withinRadiusIncludesTheCenterAndOnlyCellsWithinDistance() {
        int radius = 2;
        var cells = origin.withinRadius(radius);

        assertThat(cells).contains(origin);
        assertThat(cells).allMatch(cell -> origin.distanceTo(cell) <= radius);
        assertThat(cells).hasSize(1 + 3 * radius * (radius + 1));
    }
}
