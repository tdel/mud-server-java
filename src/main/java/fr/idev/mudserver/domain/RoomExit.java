package fr.idev.mudserver.domain;

import java.util.Objects;

public class RoomExit {

    private String direction;
    private Room sourceRoom;
    private Room targetRoom;

    public RoomExit(String direction, Room sourceRoom, Room targetRoom) {
        this.direction = direction;
        this.sourceRoom = sourceRoom;
        this.targetRoom = targetRoom;
    }

    public String getDirection() {
        return direction;
    }

    public void setDirection(String direction) {
        this.direction = direction;
    }

    public Room getSourceRoom() {
        return sourceRoom;
    }

    public void setSourceRoom(Room sourceRoom) {
        this.sourceRoom = sourceRoom;
    }

    public Room getTargetRoom() {
        return targetRoom;
    }

    public void setTargetRoom(Room targetRoom) {
        this.targetRoom = targetRoom;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof RoomExit other)) {
            return false;
        }
        return Objects.equals(direction, other.direction) && Objects.equals(sourceRoom, other.sourceRoom)
                && Objects.equals(targetRoom, other.targetRoom);
    }

    @Override
    public int hashCode() {
        return Objects.hash(direction, sourceRoom, targetRoom);
    }

    @Override
    public String toString() {
        return "RoomExit[direction=" + direction + ", sourceRoomId=" + (sourceRoom == null ? null : sourceRoom.getId())
                + ", targetRoomId=" + (targetRoom == null ? null : targetRoom.getId()) + "]";
    }
}
