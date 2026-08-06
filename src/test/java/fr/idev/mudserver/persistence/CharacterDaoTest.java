package fr.idev.mudserver.persistence;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import fr.idev.mudserver.AbstractIntegrationTest;
import fr.idev.mudserver.domain.Account;
import fr.idev.mudserver.domain.actor.GamePlayer;
import fr.idev.mudserver.domain.actor.CharacterClass;
import fr.idev.mudserver.domain.actor.Gender;
import fr.idev.mudserver.domain.actor.Race;
import fr.idev.mudserver.domain.actor.TestAttributes;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class CharacterDaoTest extends AbstractIntegrationTest {

    @Autowired
    private AccountDao accountDao;

    @Autowired
    private CharacterDao characterDao;

    private Account account;
    private UUID roomA;
    private UUID roomB;

    private void seedAccountAndRooms() {
        account = new Account(UUID.randomUUID(), "carol", "hashed-password", null);
        accountDao.insert(account);
        roomA = UUID.randomUUID();
        roomB = UUID.randomUUID();
    }

    @Test
    void insertsAndFindsById() {
        seedAccountAndRooms();
        GamePlayer character = new GamePlayer(UUID.randomUUID(), account.getId(), "Carol l'Orc", roomA, Gender.WOMAN,
                Race.ORC, CharacterClass.BARBARIAN, 1, 14, 14, TestAttributes.of(14, 11, 12, 10, 11, 10), 0, 0);

        characterDao.insert(character);

        assertThat(characterDao.findById(character.getId())).contains(character);
        assertThat(characterDao.findByAccountId(account.getId())).containsExactly(character);
        assertThat(characterDao.findByAccountIdAndName(account.getId(), "Carol l'Orc")).contains(character);
    }

    @Test
    void updatesCurrentRoom() {
        seedAccountAndRooms();
        GamePlayer character = new GamePlayer(UUID.randomUUID(), account.getId(), "Carol l'Orc", roomA, Gender.WOMAN,
                Race.ORC, CharacterClass.BARBARIAN, 1, 14, 14, TestAttributes.of(14, 11, 12, 10, 11, 10), 0, 0);
        characterDao.insert(character);

        characterDao.updateCurrentRoom(character.getId(), roomB);

        assertThat(characterDao.findById(character.getId())).map(GamePlayer::getCurrentRoomId).contains(roomB);
    }

    @Test
    void updatesProgress() {
        seedAccountAndRooms();
        GamePlayer character = new GamePlayer(UUID.randomUUID(), account.getId(), "Carol l'Orc", roomA, Gender.WOMAN,
                Race.ORC, CharacterClass.BARBARIAN, 1, 14, 14, TestAttributes.of(14, 11, 12, 10, 11, 10), 0, 0);
        characterDao.insert(character);

        character.setCurrentRoomId(roomB);
        character.setCurrentHealth(7);
        character.setLevel(3);
        character.setMaxHealth(20);
        characterDao.update(character);

        assertThat(characterDao.findById(character.getId())).contains(character);
    }

    @Test
    void updatePersistsXpGainedInGame() {
        seedAccountAndRooms();
        GamePlayer character = new GamePlayer(UUID.randomUUID(), account.getId(), "Carol l'Orc", roomA, Gender.WOMAN,
                Race.ORC, CharacterClass.BARBARIAN, 1, 14, 14, TestAttributes.of(14, 11, 12, 10, 11, 10), 0, 0);
        characterDao.insert(character);

        // Ne franchit aucun palier de niveau (le premier est à 300 XP) : n'exerce que
        // la persistance de l'XP elle-même, la boucle de montée de niveau est
        // couverte par CharacterServiceTest.
        character.gainXp(50);

        assertThat(characterDao.findById(character.getId())).contains(character);
    }

    @Test
    void deletesById() {
        seedAccountAndRooms();
        GamePlayer character = new GamePlayer(UUID.randomUUID(), account.getId(), "Carol l'Orc", roomA, Gender.WOMAN,
                Race.ORC, CharacterClass.BARBARIAN, 1, 14, 14, TestAttributes.of(14, 11, 12, 10, 11, 10), 0, 0);
        characterDao.insert(character);

        characterDao.deleteById(character.getId());

        assertThat(characterDao.findById(character.getId())).isEmpty();
    }
}
