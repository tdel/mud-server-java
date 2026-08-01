package fr.idev.mudserver.persistence;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import fr.idev.mudserver.AbstractIntegrationTest;
import fr.idev.mudserver.domain.Account;
import fr.idev.mudserver.domain.Character;
import fr.idev.mudserver.domain.Race;
import fr.idev.mudserver.domain.Room;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class CharacterDaoTest extends AbstractIntegrationTest {

    @Autowired
    private AccountDao accountDao;

    @Autowired
    private RoomDao roomDao;

    @Autowired
    private CharacterDao characterDao;

    private Account account;
    private Room roomA;
    private Room roomB;

    private void seedAccountAndRooms() {
        account = new Account(UUID.randomUUID(), "carol", "hashed-password", null);
        accountDao.insert(account);
        roomA = new Room(UUID.randomUUID(), "Salle A", "...", true);
        roomB = new Room(UUID.randomUUID(), "Salle B", "...", null);
        roomDao.insert(roomA);
        roomDao.insert(roomB);
    }

    @Test
    void insertsAndFindsById() {
        seedAccountAndRooms();
        Character character = new Character(UUID.randomUUID(), account.id(), "Carol l'Orc", roomA.id(), Race.ORC, 14,
                14, 10, 10, 14, 11, 12, 10, 11, 10);

        characterDao.insert(character);

        assertThat(characterDao.findById(character.id())).contains(character);
        assertThat(characterDao.findByAccountId(account.id())).containsExactly(character);
        assertThat(characterDao.findByAccountIdAndName(account.id(), "Carol l'Orc")).contains(character);
    }

    @Test
    void updatesCurrentRoom() {
        seedAccountAndRooms();
        Character character = new Character(UUID.randomUUID(), account.id(), "Carol l'Orc", roomA.id(), Race.ORC, 14,
                14, 10, 10, 14, 11, 12, 10, 11, 10);
        characterDao.insert(character);

        characterDao.updateCurrentRoom(character.id(), roomB.id());

        assertThat(characterDao.findById(character.id())).map(Character::currentRoomId).contains(roomB.id());
    }

    @Test
    void deletesById() {
        seedAccountAndRooms();
        Character character = new Character(UUID.randomUUID(), account.id(), "Carol l'Orc", roomA.id(), Race.ORC, 14,
                14, 10, 10, 14, 11, 12, 10, 11, 10);
        characterDao.insert(character);

        characterDao.deleteById(character.id());

        assertThat(characterDao.findById(character.id())).isEmpty();
    }
}
