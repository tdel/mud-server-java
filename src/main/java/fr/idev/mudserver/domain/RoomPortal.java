package fr.idev.mudserver.domain;

public record RoomPortal(HexCoordinate cell, String direction, RoomInstance sourceRoom, RoomInstance targetRoom,
        HexCoordinate targetCell) {
}
