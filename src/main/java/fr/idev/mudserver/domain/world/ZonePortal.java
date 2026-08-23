package fr.idev.mudserver.domain.world;

import fr.idev.mudserver.domain.map.HexCoordinate;

public record ZonePortal(HexCoordinate cell, String direction, ZoneInstance sourceZone, ZoneInstance targetZone,
        HexCoordinate targetCell) {
}
