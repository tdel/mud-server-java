package fr.idev.mudserver.domain.world;

import fr.idev.mudserver.domain.map.HexCoordinate;

public record RoomPortal(HexCoordinate cell, String direction, RoomInstance sourceRoom, RoomInstance targetRoom,
        HexCoordinate targetCell) {
}
