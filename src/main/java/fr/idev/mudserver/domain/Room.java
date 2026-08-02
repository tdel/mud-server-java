package fr.idev.mudserver.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * {@code isStartingRoom} is a nullable sentinel, not a plain boolean: NULL/TRUE
 * only, never FALSE (see V1__init_schema.sql's uniq_room_starting index).
 */
public class Room {

    private UUID id;
    private String name;
    private String description;
    private Boolean isStartingRoom;

    public Room(UUID id, String name, String description, Boolean isStartingRoom) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.isStartingRoom = isStartingRoom;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Boolean isStartingRoom() {
        return isStartingRoom;
    }

    public void setStartingRoom(Boolean startingRoom) {
        this.isStartingRoom = startingRoom;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Room other)) {
            return false;
        }
        return Objects.equals(id, other.id) && Objects.equals(name, other.name)
                && Objects.equals(description, other.description)
                && Objects.equals(isStartingRoom, other.isStartingRoom);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, description, isStartingRoom);
    }

    @Override
    public String toString() {
        return "Room[id=" + id + ", name=" + name + ", description=" + description + ", isStartingRoom="
                + isStartingRoom + "]";
    }
}
