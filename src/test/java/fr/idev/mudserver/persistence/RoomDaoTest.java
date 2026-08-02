package fr.idev.mudserver.persistence;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import fr.idev.mudserver.AbstractIntegrationTest;
import fr.idev.mudserver.domain.Room;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class RoomDaoTest extends AbstractIntegrationTest {

    @Autowired
    private RoomDao roomDao;

    @Test
    void insertsAndFindsById() {
        Room room = new Room(UUID.randomUUID(), "Place du village", "Une place pavée.", null);

        roomDao.insert(room);

        assertThat(roomDao.findById(room.getId())).contains(room);
    }

    @Test
    void onlyOneRoomCanBeMarkedAsStarting() {
        Room first = new Room(UUID.randomUUID(), "Salle A", "...", null);
        Room second = new Room(UUID.randomUUID(), "Salle B", "...", null);
        roomDao.insert(first);
        roomDao.insert(second);

        roomDao.markAsStartingRoom(first.getId());
        assertThat(roomDao.findStartingRoom()).map(Room::getId).contains(first.getId());

        roomDao.clearStartingRoom();
        roomDao.markAsStartingRoom(second.getId());
        assertThat(roomDao.findStartingRoom()).map(Room::getId).contains(second.getId());
    }

    @Test
    void findAllReturnsEveryRoom() {
        roomDao.insert(new Room(UUID.randomUUID(), "Salle A", "...", null));
        roomDao.insert(new Room(UUID.randomUUID(), "Salle B", "...", null));

        assertThat(roomDao.findAll()).hasSize(2);
    }
}
