package fr.idev.mudserver.game;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import fr.idev.mudserver.persistence.CharacterDao;
import fr.idev.mudserver.persistence.RoomDao;
import org.springframework.stereotype.Component;

import fr.idev.mudserver.domain.Character;
import fr.idev.mudserver.domain.Room;
import fr.idev.mudserver.network.Connection;

/**
 * Suit tous les joueurs actuellement dans le monde de jeu, pour toute la durée
 * de vie du process, et la session associée à chacun — sur le même principe que
 * {@link AuthWorld} : {@code Connection} n'a donc pas besoin d'exposer
 * d'accesseur de personnage.
 */
@Component
public class GameWorld {

    private final Map<UUID, Room> rooms = new ConcurrentHashMap<>();
    private final Map<Connection, Character> characters = new ConcurrentHashMap<>();

    private final CharacterDao characterDao;
    private final RoomDao roomDao;

    public GameWorld(CharacterDao characterDao, RoomDao roomDao) {
        this.characterDao = characterDao;
        this.roomDao = roomDao;
    }

    public void enterWorld(Connection connection, Character character) {
        characters.put(connection, character);
        moveCharacter(connection, character.getCurrentRoomId());
    }

    public void moveCharacter(Connection connection, UUID roomId) {
        Character character = characters.get(connection);
        Room newRoom = room(roomId);

        room(character.getCurrentRoomId()).leave(connection, character, newRoom);
        newRoom.join(connection, character);
        characterDao.updateCurrentRoom(character.getId(), roomId);
    }

    public void exitWorld(Connection connection) {
        Character character = characters.remove(connection);
        if (character == null) {
            return;
        }

        room(character.getCurrentRoomId()).disconnect(connection, character);
    }

    public Character character(Connection connection) {
        return characters.get(connection);
    }

    public Room room(UUID roomId) {
        return rooms.get(roomId);
    }

    public Optional<Room> startingRoom() {
        return rooms.values().stream().filter(room -> Boolean.TRUE.equals(room.isStartingRoom())).findFirst();
    }

    public void warmRooms() {
        for (Room room : roomDao.findAll()) {
            rooms.put(room.getId(), room);
        }
    }

    public boolean isAlreadyConnected(UUID accountId) {
        return characters.values().stream().anyMatch(character -> character.getAccountId().equals(accountId));
    }
}
