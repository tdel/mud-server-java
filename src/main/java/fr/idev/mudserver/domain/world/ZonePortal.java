package fr.idev.mudserver.domain.world;

import fr.idev.mudserver.domain.map.Position;

public record ZonePortal(Position position, String direction, ZoneInstance sourceZone, ZoneInstance targetZone,
        Position targetPosition, double triggerRadius) {
}
