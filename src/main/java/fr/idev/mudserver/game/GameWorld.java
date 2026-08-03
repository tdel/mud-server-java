package fr.idev.mudserver.game;

import java.util.List;
import java.util.Map;
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

    private final Map<UUID, RoomInstance> roomInstances = new ConcurrentHashMap<>();
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
        Room newRoom = roomDao.findById(roomId).orElseThrow();

        roomInstance(character.getCurrentRoomId()).leave(connection, character, newRoom);
        roomInstance(roomId).join(connection, character);
        characterDao.updateCurrentRoom(character.getId(), roomId);
    }

    public void exitWorld(Connection connection) {
        Character character = characters.remove(connection);
        if (character == null) {
            return;
        }

        roomInstance(character.getCurrentRoomId()).disconnect(connection, character);
    }

    public Character character(Connection connection) {
        return characters.get(connection);
    }

    public RoomInstance roomInstance(UUID roomId) {
        return roomInstances.get(roomId);
    }

    public void warmRoomInstances() {
        List<Room> rooms = roomDao.findAll();

        for (Room room : rooms) {
            roomInstances.put(room.getId(), new RoomInstance(room.getId()));
        }
    }

    public boolean isAlreadyConnected(UUID accountId) {
        return characters.values().stream().anyMatch(character -> character.getAccountId().equals(accountId));
    }
}
