package app.domain.world;

import app.domain.map.Position;

import java.util.BitSet;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MapTemplateTest {

    @Test
    void zoneAtReflectsTheZonesAttachedToTheMap() {
        CollisionGrid grid = new CollisionGrid(10, 10, 1.0, allWalkable(10, 10));
        MapTemplate template = new MapTemplate(UUID.randomUUID(), "Place du village", "description", true, grid,
                new Position(1, 1), List.of(), List.of(), List.of());
        template.setPeaceZones(List.of(new PeaceZone("Peace Zone",
                List.of(new Position(0, 0), new Position(5, 0), new Position(5, 5), new Position(0, 5)))));

        assertThat(template.zoneAt(new Position(2, 2))).isInstanceOf(PeaceZone.class);
        assertThat(template.zoneAt(new Position(8, 8))).isSameAs(NormalZone.INSTANCE);
    }

    private static BitSet allWalkable(int width, int height) {
        BitSet walkable = new BitSet(width * height);
        walkable.set(0, width * height);
        return walkable;
    }
}
