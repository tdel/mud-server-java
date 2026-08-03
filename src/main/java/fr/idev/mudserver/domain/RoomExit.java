package fr.idev.mudserver.domain;

import java.util.Objects;
import java.util.UUID;

public class RoomExit {

    private UUID id;
    private String direction;

    // Ne servent qu'à la persistance (RoomExitDao) — le code applicatif doit
    // utiliser getSourceRoom()/getTargetRoom(), attachées par
    // RoomService.warmRoomExits().
    private UUID sourceRoomId;
    private UUID targetRoomId;

    private Room sourceRoom;
    private Room targetRoom;

    public RoomExit(UUID id, String direction, UUID sourceRoomId, UUID targetRoomId) {
        this.id = id;
        this.direction = direction;
        this.sourceRoomId = sourceRoomId;
        this.targetRoomId = targetRoomId;
    }

    public void attachRooms(Room sourceRoom, Room targetRoom) {
        this.sourceRoom = sourceRoom;
        this.targetRoom = targetRoom;
    }

    public Room getSourceRoom() {
        return requireAttachedRoom(sourceRoom, "sourceRoom");
    }

    public Room getTargetRoom() {
        return requireAttachedRoom(targetRoom, "targetRoom");
    }

    private Room requireAttachedRoom(Room room, String field) {
        if (room == null) {
            throw new IllegalStateException("RoomExit " + id + " has no " + field + " attached");
        }
        return room;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getDirection() {
        return direction;
    }

    public void setDirection(String direction) {
        this.direction = direction;
    }

    public UUID getSourceRoomId() {
        return sourceRoomId;
    }

    public void setSourceRoomId(UUID sourceRoomId) {
        this.sourceRoomId = sourceRoomId;
    }

    public UUID getTargetRoomId() {
        return targetRoomId;
    }

    public void setTargetRoomId(UUID targetRoomId) {
        this.targetRoomId = targetRoomId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof RoomExit other)) {
            return false;
        }
        return Objects.equals(id, other.id) && Objects.equals(direction, other.direction)
                && Objects.equals(sourceRoomId, other.sourceRoomId) && Objects.equals(targetRoomId, other.targetRoomId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, direction, sourceRoomId, targetRoomId);
    }

    @Override
    public String toString() {
        return "RoomExit[id=" + id + ", direction=" + direction + ", sourceRoomId=" + sourceRoomId + ", targetRoomId="
                + targetRoomId + "]";
    }
}
