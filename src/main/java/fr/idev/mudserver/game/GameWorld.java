package fr.idev.mudserver.game;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import fr.idev.mudserver.persistence.CharacterDao;
import fr.idev.mudserver.persistence.RoomDao;
import org.springframework.stereotype.Component;

import fr.idev.mudserver.domain.Room;
import fr.idev.mudserver.network.Connection;

/**
 * Suit tous les joueurs actuellement dans le monde de jeu, pour toute la durée
 * de vie du process, et la session associée à chacun — remplace le
 * {@code Set<Client>} par une {@code Map<Connection, Client>} sur le même
 * principe que {@link AuthWorld} : {@code Connection} n'a donc pas besoin
 * d'exposer d'accesseur de joueur.
 */
@Component
public class GameWorld {

    private final Map<UUID, RoomInstance> roomInstances = new ConcurrentHashMap<>();
    private final Map<Connection, Client> clients = new ConcurrentHashMap<>();

    private final CharacterDao characterDao;
    private final RoomDao roomDao;

    public GameWorld(CharacterDao characterDao, RoomDao roomDao) {
        this.characterDao = characterDao;
        this.roomDao = roomDao;
    }

    public void enterWorld(Connection session, Client client) {
        clients.put(session, client);
        moveClient(client, client.character().getCurrentRoomId());
    }

    public void moveClient(Client client, UUID roomId) {
        Room newRoom = roomDao.findById(roomId).orElseThrow();

        roomInstance(client.character().getCurrentRoomId()).leave(client, newRoom);
        roomInstance(roomId).join(client);
        characterDao.updateCurrentRoom(client.character().getId(), roomId);
    }

    public void exitWorld(Connection session) {
        Client client = clients.remove(session);
        if (client == null) {
            return;
        }

        roomInstance(client.character().getCurrentRoomId()).disconnect(client);
    }

    public Client client(Connection session) {
        return clients.get(session);
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
        return clients.values().stream().anyMatch(client -> client.character().getAccountId().equals(accountId));
    }
}
