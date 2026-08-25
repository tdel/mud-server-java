package app.domain.world;

import app.domain.map.Position;

import java.util.UUID;

public record ZoneTemplatePortal(Position position, String direction, UUID targetZoneTemplateId,
        Position targetPosition, double triggerRadius) {
}
