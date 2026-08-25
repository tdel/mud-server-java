package app.domain.world;

import app.domain.map.Position;

public record ZonePortal(Position position, String direction, ZoneInstance sourceZone, ZoneInstance targetZone,
        Position targetPosition, double triggerRadius) {
}
