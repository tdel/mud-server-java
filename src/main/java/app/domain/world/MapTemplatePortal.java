package app.domain.world;

import app.domain.map.Position;

import java.util.UUID;

public record MapTemplatePortal(Position position, String direction, UUID targetMapTemplateId,
        Position targetPosition, double triggerRadius) {
}
