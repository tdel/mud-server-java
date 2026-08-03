package fr.idev.mudserver.game;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import fr.idev.mudserver.domain.Character;
import fr.idev.mudserver.domain.Room;
import fr.idev.mudserver.persistence.CharacterDao;
import fr.idev.mudserver.persistence.RoomDao;

/**
 * Point d'entrée unique pour le cache des rooms et les déplacements entre
 * elles. {@code Room} (depuis la fusion de RoomInstance, voir historique) reste
 * à la fois l'entité persistée et le conteneur runtime des personnages présents
 * ; {@code RoomService} est la couche cache/cycle de vie au-dessus
 * (warm/lookup/déplacement) — les mutations d'appartenance
 * ({@code join}/{@code leave}/{@code disconnect}/{@code broadcast}) restent
 * portées par {@link Room} lui-même, appelées directement sur l'objet renvoyé
 * par {@link #room}.
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

    public Optional<Room> startingRoom() {
        return rooms.values().stream().filter(room -> Boolean.TRUE.equals(room.isStartingRoom())).findFirst();
    }

    public void moveCharacter(Character character, UUID roomId) {
        Room newRoom = room(roomId);

        room(character.getCurrentRoomId()).leave(character, newRoom);
        newRoom.join(character);
        characterDao.updateCurrentRoom(character.getId(), roomId);
    }
}
