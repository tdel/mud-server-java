package fr.idev.mudserver.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HexDirectionTest {

    @Test
    void eastAndWestAreTrueOppositeDirectionsWithNoDiagonalComponent() {
        assertThat(HexDirection.E.dr()).isZero();
        assertThat(HexDirection.W.dr()).isZero();
        assertThat(HexDirection.E.dq()).isEqualTo(1);
        assertThat(HexDirection.W.dq()).isEqualTo(-1);
    }

    @Test
    void oppositeIsSymmetricAndCancelsTheDeltaForEveryDirection() {
        for (HexDirection direction : HexDirection.values()) {
            assertThat(direction.opposite().opposite()).isEqualTo(direction);
            assertThat(direction.opposite().dq()).isEqualTo(-direction.dq());
            assertThat(direction.opposite().dr()).isEqualTo(-direction.dr());
        }
    }

    @Test
    void fromTokenAcceptsShortTokensCaseInsensitively() {
        assertThat(HexDirection.fromToken("e")).contains(HexDirection.E);
        assertThat(HexDirection.fromToken("W")).contains(HexDirection.W);
        assertThat(HexDirection.fromToken("Ne")).contains(HexDirection.NE);
        assertThat(HexDirection.fromToken("sw")).contains(HexDirection.SW);
    }

    @Test
    void fromTokenAcceptsFrenchCompassWords() {
        assertThat(HexDirection.fromToken("est")).contains(HexDirection.E);
        assertThat(HexDirection.fromToken("ouest")).contains(HexDirection.W);
        assertThat(HexDirection.fromToken("nord-est")).contains(HexDirection.NE);
        assertThat(HexDirection.fromToken("nord-ouest")).contains(HexDirection.NW);
        assertThat(HexDirection.fromToken("sud-est")).contains(HexDirection.SE);
        assertThat(HexDirection.fromToken("sud-ouest")).contains(HexDirection.SW);
    }

    @Test
    void fromTokenRejectsUnknownWords() {
        assertThat(HexDirection.fromToken("nord")).isEmpty();
        assertThat(HexDirection.fromToken("gibberish")).isEmpty();
    }
}
