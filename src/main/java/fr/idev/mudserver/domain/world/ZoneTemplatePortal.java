package fr.idev.mudserver.domain.world;

import fr.idev.mudserver.domain.map.HexCoordinate;

import java.util.UUID;

public record ZoneTemplatePortal(HexCoordinate cell, String direction, UUID targetZoneTemplateId,
        HexCoordinate targetCell) {
}
