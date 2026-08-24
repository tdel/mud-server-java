package fr.idev.mudserver.domain.map;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PositionTest {

    @Test
    void distanceToComputesEuclideanDistance() {
        Position a = new Position(0, 0);
        Position b = new Position(3, 4);

        assertThat(a.distanceTo(b)).isEqualTo(5.0);
    }

    @Test
    void normalizedReturnsUnitVector() {
        Position vector = new Position(3, 4);

        Position normalized = vector.normalized();

        assertThat(normalized.length()).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    void normalizedOfZeroVectorReturnsZero() {
        Position zero = new Position(0, 0);

        assertThat(zero.normalized()).isEqualTo(new Position(0, 0));
    }

    @Test
    void moveTowardStopsAtTargetWhenCloserThanMaxDistance() {
        Position start = new Position(0, 0);
        Position target = new Position(1, 0);

        Position result = start.moveToward(target, 5);

        assertThat(result).isEqualTo(target);
    }

    @Test
    void moveTowardNeverOvershootsMaxDistance() {
        Position start = new Position(0, 0);
        Position target = new Position(10, 0);

        Position result = start.moveToward(target, 2);

        assertThat(result.distanceTo(start)).isCloseTo(2.0, org.assertj.core.data.Offset.offset(1e-9));
    }
}
