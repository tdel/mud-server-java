package fr.idev.mudserver.domain;

import java.util.UUID;

public record RoomTemplatePortal(HexCoordinate cell, String direction, UUID targetRoomTemplateId,
        HexCoordinate targetCell) {
}
