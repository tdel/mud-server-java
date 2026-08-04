package fr.idev.mudserver.persistence;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import fr.idev.mudserver.AbstractIntegrationTest;
import fr.idev.mudserver.domain.Account;
import fr.idev.mudserver.domain.GamePlayer;
import fr.idev.mudserver.domain.CharacterClass;
import fr.idev.mudserver.domain.Race;
import fr.idev.mudserver.domain.TestAttributes;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class AccountDaoTest extends AbstractIntegrationTest {

    @Autowired
    private AccountDao accountDao;

    @Autowired
    private CharacterDao characterDao;

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

        GamePlayer character = new GamePlayer(UUID.randomUUID(), account.getId(), "Bob le Nain", UUID.randomUUID(),
                Race.DWARF, CharacterClass.FIGHTER, 1, 12, 12, TestAttributes.of(12, 10, 12, 10, 10, 10));
        characterDao.insert(character);

        accountDao.updateCurrentCharacter(account.getId(), character.getId());

        assertThat(accountDao.findById(account.getId())).map(Account::getCurrentCharacterId)
                .contains(character.getId());
    }

    @Test
    void clearsCurrentCharacterBackToNull() {
        Account account = new Account(UUID.randomUUID(), "carol", "hashed-password", null);
        accountDao.insert(account);

        GamePlayer character = new GamePlayer(UUID.randomUUID(), account.getId(), "Carol", UUID.randomUUID(),
                Race.DWARF, CharacterClass.FIGHTER, 1, 12, 12, TestAttributes.of(12, 10, 12, 10, 10, 10));
        characterDao.insert(character);
        accountDao.updateCurrentCharacter(account.getId(), character.getId());

        accountDao.updateCurrentCharacter(account.getId(), null);

        assertThat(accountDao.findById(account.getId())).map(Account::getCurrentCharacterId).isEmpty();
    }
}
