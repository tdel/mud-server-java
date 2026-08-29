package app.domain.world;

import app.domain.map.Position;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PeaceZoneTest {

    private final PeaceZone square = new PeaceZone("Peace Zone",
            List.of(new Position(0, 0), new Position(10, 0), new Position(10, 10), new Position(0, 10)));

    @Test
    void containsPositionInsideThePolygon() {
        assertThat(square.contains(new Position(5, 5))).isTrue();
    }

    @Test
    void doesNotContainPositionOutsideThePolygon() {
        assertThat(square.contains(new Position(15, 5))).isFalse();
    }

    @Test
    void doesNotContainPositionOnTheOtherSideOfANearbyEdge() {
        assertThat(square.contains(new Position(5, 10.5))).isFalse();
    }
}
