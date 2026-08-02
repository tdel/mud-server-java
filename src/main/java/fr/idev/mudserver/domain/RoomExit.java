package fr.idev.mudserver.domain;

import java.util.Objects;
import java.util.UUID;

public class RoomExit {

    private UUID id;
    private String direction;
    private UUID sourceRoomId;
    private UUID targetRoomId;

    public RoomExit(UUID id, String direction, UUID sourceRoomId, UUID targetRoomId) {
        this.id = id;
        this.direction = direction;
        this.sourceRoomId = sourceRoomId;
        this.targetRoomId = targetRoomId;
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
