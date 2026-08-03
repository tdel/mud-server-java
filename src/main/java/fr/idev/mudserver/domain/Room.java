package fr.idev.mudserver.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import fr.idev.mudserver.network.Connection;
import fr.idev.mudserver.network.OutputMessage;
import fr.idev.mudserver.network.message.ingame.CharacterDisconnected;
import fr.idev.mudserver.network.message.ingame.CharacterJoinedRoom;
import fr.idev.mudserver.network.message.ingame.CharacterLeftRoom;

/**
 * {@code isStartingRoom} is a nullable sentinel, not a plain boolean: NULL/TRUE
 * only, never FALSE (see V1__init_schema.sql's uniq_room_starting index).
 *
 * <p>
 * {@code clients} suit les joueurs actuellement présents dans la room, et sert
 * de point d'entrée pour diffuser un message à tout le monde dedans.
 * Rejoindre/quitter notifie toujours la room, l'appelant n'a jamais besoin d'y
 * penser lui-même. {@code ConcurrentHashMap} remplace le
 * {@code SplObjectStorage} PHP — son itérateur est faiblement cohérent (jamais
 * de {@code ConcurrentModificationException}, tolère un ajout/retrait pendant
 * l'itération), donc {@link #broadcast} n'a pas besoin du snapshot défensif que
 * fait la version PHP avant d'itérer. Ce champ n'est jamais persisté ni pris en
 * compte par {@link #equals}/{@link #hashCode} : il ne représente rien en base,
 * uniquement l'état vivant du process.
 */
public class Room {

    private UUID id;
    private String name;
    private String description;
    private Boolean isStartingRoom;

    private final Map<Connection, Character> clients = new ConcurrentHashMap<>();

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

    public void join(Connection connection, Character character) {
        character.setCurrentRoomId(id);
        clients.put(connection, character);
        broadcast(new CharacterJoinedRoom(character.getName()), connection);
    }

    public void leave(Connection connection, Character character, Room destination) {
        clients.remove(connection);
        broadcast(new CharacterLeftRoom(character.getName(), destination.getName()), connection);
    }

    public void disconnect(Connection connection, Character character) {
        clients.remove(connection);
        broadcast(new CharacterDisconnected(character.getName()), connection);
    }

    public Character findCharacterByName(String name) {
        for (Character character : clients.values()) {
            if (character.getName().equalsIgnoreCase(name)) {
                return character;
            }
        }
        return null;
    }

    public List<Character> characters() {
        return new ArrayList<>(clients.values());
    }

    public void broadcast(OutputMessage message, Connection exclude) {
        for (Map.Entry<Connection, Character> entry : clients.entrySet()) {
            if (entry.getKey() == exclude) {
                continue;
            }
            entry.getKey().send(message);
        }
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
