package fr.idev.mudserver.game;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import fr.idev.mudserver.domain.Room;
import fr.idev.mudserver.network.Connection;

/**
 * Suit tous les joueurs actuellement dans le monde de jeu, pour toute la durée
 * de vie du process, et la session associée à chacun — remplace le
 * {@code Set<PlayerInstance>} par une {@code Map<Connection, PlayerInstance>}
 * sur le même principe que {@link AuthWorld} : {@code Connection} n'a donc pas
 * besoin d'exposer d'accesseur de joueur.
 */
@Component
public class GameWorld {

    private final Map<UUID, RoomInstance> roomInstances = new ConcurrentHashMap<>();
    private final Map<Connection, PlayerInstance> players = new ConcurrentHashMap<>();

    public GameWorld() {

    }

    public void enterWorld(Connection session, PlayerInstance player) {
        players.put(session, player);
        roomInstance(player.currentRoomId()).join(player);
    }

    public void exitWorld(Connection session) {
        PlayerInstance player = players.remove(session);
        if (player == null) {
            return;
        }
        roomInstance(player.currentRoomId()).disconnect(player);
    }

    public PlayerInstance player(Connection session) {
        return players.get(session);
    }

    public RoomInstance roomInstance(UUID roomId) {
        return roomInstances.computeIfAbsent(roomId, RoomInstance::new);
    }

    /**
     * Appelé une fois au démarrage du serveur, avant qu'aucune connexion ne soit
     * acceptée — ainsi {@link #roomInstance} n'a jamais besoin d'être thread-safe
     * en écriture concurrente pour la première visite d'une room (contrairement au
     * PHP, ici {@code computeIfAbsent} le serait de toute façon, mais le warm-up
     * évite même la question).
     */
    public void warmRoomInstances(Iterable<Room> rooms) {
        for (Room room : rooms) {
            roomInstance(room.getId());
        }
    }

    public boolean isAlreadyConnected(UUID accountId) {
        return players.values().stream().anyMatch(player -> player.character().getAccountId().equals(accountId));
    }
}
