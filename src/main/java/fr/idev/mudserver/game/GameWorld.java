package fr.idev.mudserver.game;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import fr.idev.mudserver.domain.Room;
import fr.idev.mudserver.persistence.CharacterDao;

/**
 * Suit tous les joueurs actuellement dans le monde de jeu, pour toute la durée de vie du
 * process. Agnostique du transport : ne connaît que {@link PlayerInstance} et les identifiants
 * de room, jamais telnet (ni aucun autre transport) directement.
 */
@Component
public class GameWorld {

    private final Map<UUID, RoomInstance> roomInstances = new ConcurrentHashMap<>();
    private final Set<PlayerInstance> players = ConcurrentHashMap.newKeySet();
    private final CharacterDao characterDao;

    public GameWorld(CharacterDao characterDao) {
        this.characterDao = characterDao;
    }

    public void enterWorld(PlayerInstance player) {
        players.add(player);
        roomInstance(player.currentRoomId()).join(player);
    }

    public void exitWorld(PlayerInstance player) {
        if (!players.remove(player)) {
            return;
        }
        roomInstance(player.currentRoomId()).disconnect(player);
    }

    public RoomInstance roomInstance(UUID roomId) {
        return roomInstances.computeIfAbsent(roomId, id -> new RoomInstance(id, characterDao));
    }

    /**
     * Appelé une fois au démarrage du serveur, avant qu'aucune connexion ne soit acceptée —
     * ainsi {@link #roomInstance} n'a jamais besoin d'être thread-safe en écriture concurrente
     * pour la première visite d'une room (contrairement au PHP, ici {@code computeIfAbsent} le
     * serait de toute façon, mais le warm-up évite même la question).
     */
    public void warmRoomInstances(Iterable<Room> rooms) {
        for (Room room : rooms) {
            roomInstance(room.id());
        }
    }

    public boolean isAlreadyConnected(UUID accountId) {
        for (PlayerInstance player : players) {
            if (player.character().accountId().equals(accountId)) {
                return true;
            }
        }
        return false;
    }
}
