package fr.idev.mudserver.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import fr.idev.mudserver.network.OutputMessage;
import fr.idev.mudserver.network.message.ingame.CharacterDisconnected;
import fr.idev.mudserver.network.message.ingame.CharacterJoinedRoom;
import fr.idev.mudserver.network.message.ingame.CharacterLeftRoom;

/**
 * {@code isStartingRoom} is a nullable sentinel, not a plain boolean: NULL/TRUE
 * only, never FALSE. Rooms are loaded from {@code data/rooms.json} (see
 * {@code RoomService.warmRooms()}), not persisted in DB, so "at most one
 * starting room" is no longer enforced by a Postgres unique index — it's
 * validated at the application layer in {@code RoomService.loadRooms()}
 * instead.
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
 * uniquement l'état vivant du process. Clé sur l'id du personnage plutôt que sa
 * connexion : {@link Character} porte désormais sa propre connexion.
 */
public class Room {

    private UUID id;
    private String name;
    private String description;
    private Boolean isStartingRoom;

    private final Map<UUID, Character> clients = new ConcurrentHashMap<>();
    private final List<Item> items = new CopyOnWriteArrayList<>();
    private final List<RoomExit> exits = new CopyOnWriteArrayList<>();

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

    public void join(Character character) {
        character.setCurrentRoom(this);
        clients.put(character.getId(), character);
        broadcast(new CharacterJoinedRoom(character.getName()), character);
    }

    public void leave(Character character) {
        clients.remove(character.getId());
        broadcast(new CharacterLeftRoom(character.getName()), character);
    }

    public void disconnect(Character character) {
        clients.remove(character.getId());
        broadcast(new CharacterDisconnected(character.getName()), character);
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

    public List<Item> getItems() {
        return List.copyOf(items);
    }

    public Optional<Item> findOneByName(String name) {
        return items.stream().filter(item -> item.getName().equalsIgnoreCase(name)).findFirst();
    }

    public void addItem(Item item) {
        items.add(item);
    }

    public void removeItem(Item item) {
        items.remove(item);
    }

    public void setItems(List<Item> items) {
        this.items.clear();
        this.items.addAll(items);
    }

    public List<RoomExit> getExits() {
        return List.copyOf(exits);
    }

    public void setExits(List<RoomExit> exits) {
        this.exits.clear();
        this.exits.addAll(exits);
    }

    public Optional<RoomExit> findOneByDirection(String direction) {
        return exits.stream().filter(exit -> exit.getDirection().equals(direction)).findFirst();
    }

    public void broadcast(OutputMessage message, Character exclude) {
        for (Character character : clients.values()) {
            if (character == exclude || character.getConnection() == null) {
                continue;
            }
            character.getConnection().send(message);
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
