package app.domain.actor;

import app.domain.actor.instance.MonsterInstance;
import app.domain.map.Position;
import app.domain.world.CollisionGrid;
import app.domain.world.MapInstance;
import app.domain.world.MapTemplate;
import app.domain.world.NormalZone;
import app.domain.world.PeaceZone;
import app.domain.world.WorldInstance;

import java.time.Instant;
import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AbstractCharacterZoneTest {

    @Test
    void setPositionUpdatesZoneOnlyWhenCrossingAZoneBoundary() {
        CollisionGrid grid = new CollisionGrid(20, 20, 1.0, allWalkable(20, 20));
        MapTemplate template = new MapTemplate(UUID.randomUUID(), "map", "description", true, grid, new Position(1, 1),
                List.of(), List.of(), List.of());
        PeaceZone peaceZone = new PeaceZone("Peace Zone",
                List.of(new Position(0, 0), new Position(10, 0), new Position(10, 10), new Position(0, 10)));
        template.setPeaceZones(List.of(peaceZone));
        MapInstance map = new MapInstance(UUID.randomUUID(), template,
                new WorldInstance(UUID.randomUUID(), UUID.randomUUID(), Instant.now()));

        MonsterInstance monster = new MonsterInstance(UUID.randomUUID(), "monster", UUID.randomUUID(), map.getId(),
                Map.of(Attribute.STRENGTH, 10), 10, new Position(15, 15));
        monster.setCurrentMap(map);

        assertThat(monster.getZone()).isSameAs(NormalZone.INSTANCE);

        monster.setPosition(new Position(5, 5));
        assertThat(monster.getZone()).isSameAs(peaceZone);

        monster.setPosition(new Position(6, 6));
        assertThat(monster.getZone()).isSameAs(peaceZone);

        monster.setPosition(new Position(15, 15));
        assertThat(monster.getZone()).isSameAs(NormalZone.INSTANCE);
    }

    private static BitSet allWalkable(int width, int height) {
        BitSet walkable = new BitSet(width * height);
        walkable.set(0, width * height);
        return walkable;
    }
}
