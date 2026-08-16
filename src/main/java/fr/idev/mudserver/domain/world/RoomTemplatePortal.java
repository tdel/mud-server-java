package fr.idev.mudserver.domain.world;

import fr.idev.mudserver.domain.map.HexCoordinate;

import java.util.UUID;

public record RoomTemplatePortal(HexCoordinate cell, String direction, UUID targetRoomTemplateId,
        HexCoordinate targetCell) {
}
