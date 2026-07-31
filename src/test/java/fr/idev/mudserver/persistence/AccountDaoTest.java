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
class AccountDaoTest extends AbstractIntegrationTest {

    @Autowired
    private AccountDao accountDao;

    @Autowired
    private CharacterDao characterDao;

    @Autowired
    private RoomDao roomDao;

    @Test
    void insertsAndFindsByLogin() {
        Account account = new Account(UUID.randomUUID(), "alice", "hashed-password", null);

        accountDao.insert(account);

        assertThat(accountDao.findByLogin("alice")).contains(account);
        assertThat(accountDao.findByLogin("inconnu")).isEmpty();
    }

    @Test
    void updatesCurrentCharacter() {
        Account account = new Account(UUID.randomUUID(), "bob", "hashed-password", null);
        accountDao.insert(account);

        Room startingRoom = new Room(UUID.randomUUID(), "Place du village", "...", true);
        roomDao.insert(startingRoom);
        Character character = new Character(
                UUID.randomUUID(), account.id(), "Bob le Nain", startingRoom.id(), Race.DWARF,
                12, 12, 10, 10, 12, 10, 12, 10, 10, 10
        );
        characterDao.insert(character);

        accountDao.updateCurrentCharacter(account.id(), character.id());

        assertThat(accountDao.findById(account.id()))
                .map(Account::currentCharacterId)
                .contains(character.id());
    }
}
