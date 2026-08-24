package fr.idev.mudserver.domain.world;

import fr.idev.mudserver.domain.map.Position;

import java.util.UUID;

public record ZoneTemplatePortal(Position position, String direction, UUID targetZoneTemplateId,
        Position targetPosition, double triggerRadius) {
}
