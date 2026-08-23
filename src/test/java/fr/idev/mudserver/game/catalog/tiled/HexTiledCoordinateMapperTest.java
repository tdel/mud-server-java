package fr.idev.mudserver.game.catalog.tiled;

import static org.assertj.core.api.Assertions.assertThat;

import fr.idev.mudserver.domain.map.HexCoordinate;
import org.junit.jupiter.api.Test;

class HexTiledCoordinateMapperTest {

    private static final int TILE_WIDTH = 32;
    private static final int TILE_HEIGHT = 28;
    private static final int HEX_SIDE_LENGTH = 8;

    @Test
    void offsetToAxialAndBackRoundTripsForEvenAndOddRows() {
        for (int row = 0; row < 6; row++) {
            for (int col = 0; col < 6; col++) {
                HexCoordinate axial = HexTiledCoordinateMapper.offsetToAxial(col, row);
                int[] offset = HexTiledCoordinateMapper.axialToOffset(axial);
                assertThat(offset).containsExactly(col, row);
            }
        }
    }

    @Test
    void offsetToAxialMatchesKnownReferencePoints() {
        assertThat(HexTiledCoordinateMapper.offsetToAxial(0, 0)).isEqualTo(new HexCoordinate(0, 0));
        assertThat(HexTiledCoordinateMapper.offsetToAxial(3, 0)).isEqualTo(new HexCoordinate(3, 0));
        // ligne impaire (row=1) décalée d'une demi-case vers la droite : même colonne
        // visuelle que col=0 en row=0 correspond à q=0 (pas -1) une fois décalée.
        assertThat(HexTiledCoordinateMapper.offsetToAxial(0, 1)).isEqualTo(new HexCoordinate(0, 1));
        assertThat(HexTiledCoordinateMapper.offsetToAxial(0, 2)).isEqualTo(new HexCoordinate(-1, 2));
    }

    @Test
    void pixelToAxialInvertsCellCenterPixelAcrossAGrid() {
        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                double[] center = HexTiledCoordinateMapper.cellCenterPixel(col, row, TILE_WIDTH, TILE_HEIGHT,
                        HEX_SIDE_LENGTH);
                HexCoordinate recovered = HexTiledCoordinateMapper.pixelToAxial(center[0], center[1], TILE_WIDTH,
                        TILE_HEIGHT, HEX_SIDE_LENGTH);
                assertThat(recovered).isEqualTo(HexTiledCoordinateMapper.offsetToAxial(col, row));
            }
        }
    }
}
