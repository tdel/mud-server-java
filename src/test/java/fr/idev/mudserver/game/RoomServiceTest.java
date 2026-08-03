package fr.idev.mudserver.game;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import fr.idev.mudserver.AbstractIntegrationTest;
import fr.idev.mudserver.domain.Account;
import fr.idev.mudserver.domain.Character;
import fr.idev.mudserver.domain.Race;
import fr.idev.mudserver.domain.Room;
import fr.idev.mudserver.domain.RoomExit;
import fr.idev.mudserver.persistence.AccountDao;
import fr.idev.mudserver.persistence.CharacterDao;
import fr.idev.mudserver.persistence.RoomDao;
import fr.idev.mudserver.persistence.RoomExitDao;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class RoomServiceTest extends AbstractIntegrationTest {

    @Autowired
    private RoomService roomService;

    @Autowired
    private RoomDao roomDao;

    @Autowired
    private AccountDao accountDao;

    @Autowired
    private CharacterDao characterDao;

    @Autowired
    private RoomExitDao roomExitDao;

    @Test
    void warmRoomsPopulatesTheCacheFromTheDatabase() {
        Room room = new Room(UUID.randomUUID(), "Salle A", "...", null);
        roomDao.insert(room);

        roomService.warmRooms();

        assertThat(roomService.allRooms()).contains(room);
    }

    @Test
    void startingRoomFindsTheRoomMarkedAsStarting() {
        Room notStarting = new Room(UUID.randomUUID(), "Salle A", "...", null);
        Room starting = new Room(UUID.randomUUID(), "Salle B", "...", true);
        roomDao.insert(notStarting);
        roomDao.insert(starting);

        roomService.warmRooms();

        assertThat(roomService.startingRoom()).map(Room::getId).contains(starting.getId());
    }

    @Test
    void startingRoomIsEmptyWhenNoRoomIsMarkedAsStarting() {
        roomDao.insert(new Room(UUID.randomUUID(), "Salle A", "...", null));

        roomService.warmRooms();

        assertThat(roomService.startingRoom()).isEmpty();
    }

    @Test
    void moveCharacterJoinsTheNewRoomAndPersistsIt() {
        Room origin = new Room(UUID.randomUUID(), "Salle A", "...", null);
        Room destination = new Room(UUID.randomUUID(), "Salle B", "...", null);
        roomDao.insert(origin);
        roomDao.insert(destination);
        roomService.warmRooms();

        Account account = new Account(UUID.randomUUID(), "erin", "hashed-password", null);
        accountDao.insert(account);
        Character character = new Character(UUID.randomUUID(), account.getId(), "Erin", origin.getId(), Race.HUMAN, 10,
                10, 10, 10, 10, 10, 10, 10, 10, 10);
        characterDao.insert(character);
        room(origin.getId()).join(character);

        character.moveToRoom(room(destination.getId()));

        assertThat(room(destination.getId()).characters()).extracting(Character::getId).contains(character.getId());
        assertThat(room(origin.getId()).characters()).extracting(Character::getId).doesNotContain(character.getId());
        assertThat(characterDao.findById(character.getId())).map(Character::getCurrentRoomId)
                .contains(destination.getId());
    }

    @Test
    void warmRoomExitsPopulatesTheCacheAndAttachesTheSourceAndTargetRooms() {
        Room source = new Room(UUID.randomUUID(), "Place du village", "...", null);
        Room target = new Room(UUID.randomUUID(), "Forêt", "...", null);
        roomDao.insert(source);
        roomDao.insert(target);
        roomExitDao.insert(new RoomExit(UUID.randomUUID(), "nord", source.getId(), target.getId()));

        roomService.warmRooms();
        roomService.warmRoomExits(roomService.allRooms());

        Room warmedSource = room(source.getId());
        assertThat(warmedSource.getExits()).extracting(RoomExit::getDirection).containsExactly("nord");
        assertThat(warmedSource.findOneByDirection("nord")).map(RoomExit::getSourceRoom).contains(warmedSource);
        assertThat(warmedSource.findOneByDirection("nord")).map(RoomExit::getTargetRoom).map(Room::getId)
                .contains(target.getId());
        assertThat(warmedSource.findOneByDirection("sud")).isEmpty();
    }

    @Test
    void spawnCharacterResolvesTheCurrentRoomFromCurrentRoomIdAndJoinsIt() {
        Room room = new Room(UUID.randomUUID(), "Salle A", "...", null);
        roomDao.insert(room);
        roomService.warmRooms();

        Account account = new Account(UUID.randomUUID(), "finn", "hashed-password", null);
        accountDao.insert(account);
        Character character = new Character(UUID.randomUUID(), account.getId(), "Finn", room.getId(), Race.HUMAN, 10,
                10, 10, 10, 10, 10, 10, 10, 10, 10);
        characterDao.insert(character);

        roomService.spawnCharacter(character);

        assertThat(character.getCurrentRoom()).isEqualTo(room);
        assertThat(character.getCurrentRoom().characters()).extracting(Character::getId).contains(character.getId());
    }

    private Room room(UUID roomId) {
        return roomService.allRooms().stream().filter(room -> room.getId().equals(roomId)).findFirst().orElseThrow();
    }
}
