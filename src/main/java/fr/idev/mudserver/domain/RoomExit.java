package fr.idev.mudserver.domain;

import java.util.UUID;

public record RoomExit(UUID id, String direction, UUID sourceRoomId, UUID targetRoomId) {
}
