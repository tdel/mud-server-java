package app.domain.world;

import app.domain.map.Position;

public record MapPortal(Position position, String direction, MapInstance sourceMap, MapInstance targetMap,
        Position targetPosition, double triggerRadius) {
}
