package fr.idev.mudserver.persistence;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import fr.idev.mudserver.AbstractIntegrationTest;
import fr.idev.mudserver.domain.Room;
import fr.idev.mudserver.domain.RoomExit;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class RoomExitDaoTest extends AbstractIntegrationTest {

    @Autowired
    private RoomDao roomDao;

    @Autowired
    private RoomExitDao roomExitDao;

    @Test
    void insertsAndFindsBySourceRoom() {
        Room source = new Room(UUID.randomUUID(), "Place du village", "...", null);
        Room target = new Room(UUID.randomUUID(), "Forêt", "...", null);
        roomDao.insert(source);
        roomDao.insert(target);

        RoomExit exit = new RoomExit(UUID.randomUUID(), "nord", source.id(), target.id());
        roomExitDao.insert(exit);

        assertThat(roomExitDao.findBySourceRoomId(source.id())).containsExactly(exit);
        assertThat(roomExitDao.findBySourceRoomIdAndDirection(source.id(), "nord")).contains(exit);
        assertThat(roomExitDao.findBySourceRoomIdAndDirection(source.id(), "sud")).isEmpty();
    }
}
