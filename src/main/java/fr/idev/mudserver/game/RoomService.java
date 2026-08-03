package fr.idev.mudserver.game;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import fr.idev.mudserver.domain.Room;
import fr.idev.mudserver.domain.event.CharacterMovedToRoom;
import fr.idev.mudserver.domain.event.CharacterSpawnedToRoom;
import fr.idev.mudserver.persistence.CharacterDao;
import fr.idev.mudserver.persistence.RoomDao;

/**
 * Point d'entrée unique pour le cache des rooms. {@code Room} (depuis la fusion
 * de RoomInstance, voir historique) reste à la fois l'entité persistée et le
 * conteneur runtime des personnages présents ; {@code RoomService} est la
 * couche cache/cycle de vie au-dessus (warm/lookup) — les mutations
 * d'appartenance ({@code join}/{@code leave}/{@code disconnect}/
 * {@code broadcast}) restent portées par {@link Room} lui-même, appelées
 * directement sur l'objet renvoyé par {@link #room}, ou via
 * {@code Character#moveToRoom}/{@code Character#spawnToRoom}. Cette classe
 * réagit ensuite aux événements qu'ils publient pour répercuter le changement
 * en base.
 */
@Service
public class RoomService {

    private final Map<UUID, Room> rooms = new ConcurrentHashMap<>();

    private final RoomDao roomDao;
    private final CharacterDao characterDao;

    public RoomService(RoomDao roomDao, CharacterDao characterDao) {
        this.roomDao = roomDao;
        this.characterDao = characterDao;
    }

    public void warmRooms() {
        for (Room room : roomDao.findAll()) {
            rooms.put(room.getId(), room);
        }
    }

    public Room room(UUID roomId) {
        return rooms.get(roomId);
    }

    public Collection<Room> allRooms() {
        return rooms.values();
    }

    public Optional<Room> startingRoom() {
        return rooms.values().stream().filter(room -> Boolean.TRUE.equals(room.isStartingRoom())).findFirst();
    }

    @EventListener
    void onCharacterMovedToRoom(CharacterMovedToRoom event) {
        event.character().setCurrentRoomId(event.to().getId());
        characterDao.updateCurrentRoom(event.character().getId(), event.to().getId());
    }

    @EventListener
    void onCharacterSpawnedToRoom(CharacterSpawnedToRoom event) {
        event.character().setCurrentRoomId(event.room().getId());
        characterDao.updateCurrentRoom(event.character().getId(), event.room().getId());
    }
}
