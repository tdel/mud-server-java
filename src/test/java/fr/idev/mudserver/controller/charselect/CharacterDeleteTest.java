package fr.idev.mudserver.controller.charselect;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import fr.idev.mudserver.AbstractIntegrationTest;
import fr.idev.mudserver.controller.RecordingConnection;
import fr.idev.mudserver.domain.Account;
import fr.idev.mudserver.domain.WorldInstance;
import fr.idev.mudserver.domain.actor.CharacterClass;
import fr.idev.mudserver.domain.actor.Gender;
import fr.idev.mudserver.domain.actor.GamePlayer;
import fr.idev.mudserver.domain.actor.Race;
import fr.idev.mudserver.domain.actor.TestAttributes;
import fr.idev.mudserver.game.AuthWorld;
import fr.idev.mudserver.game.RoomService;
import fr.idev.mudserver.game.WorldInstanceService;
import fr.idev.mudserver.network.message.Usage;
import fr.idev.mudserver.network.message.charselect.CharacterCurrentlyInGame;
import fr.idev.mudserver.network.message.charselect.CharacterDeleted;
import fr.idev.mudserver.network.message.charselect.NoCharacterInWorld;
import fr.idev.mudserver.network.message.charselect.NoCharacterNamed;
import fr.idev.mudserver.persistence.AccountDao;
import fr.idev.mudserver.persistence.CharacterDao;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class CharacterDeleteTest extends AbstractIntegrationTest {

    @Autowired
    private CharacterDelete characterDelete;

    @Autowired
    private AuthWorld authWorld;

    @Autowired
    private WorldInstanceService worldInstanceService;

    @Autowired
    private RoomService roomService;

    @Autowired
    private AccountDao accountDao;

    @Autowired
    private CharacterDao characterDao;

    @Test
    void emptyNameSendsUsage() {
        RecordingConnection connection = enterCharSelect("p1");

        characterDelete.onReceive(connection, "");

        assertThat(connection.received).containsExactly(new Usage("character-delete <name>"));
    }

    @Test
    void unknownNameSendsNoCharacterNamedAndStatus() {
        RecordingConnection connection = enterCharSelect("p2");

        characterDelete.onReceive(connection, "Ghost");

        assertThat(connection.received).anyMatch(message -> message.equals(new NoCharacterNamed("Ghost")));
        assertThat(connection.received).anyMatch(NoCharacterInWorld.class::isInstance);
    }

    @Test
    void characterCurrentlyInGameRefusesDeletion() {
        RecordingConnection connection = enterCharSelect("p3");
        Account account = authWorld.account(connection);
        GamePlayer character = new GamePlayer(UUID.randomUUID(), account.getId(), "Hero", UUID.randomUUID(), Gender.MAN,
                Race.HUMAN, CharacterClass.FIGHTER, 1, 10, 10, TestAttributes.of(10, 10, 10, 10, 10, 10), 0, 0);
        character.setWorldInstanceId(WorldInstance.DEFAULT_ID);
        characterDao.insert(character);
        RecordingConnection observerConnection = new RecordingConnection();
        worldInstanceService.enterCharSelect(observerConnection,
                worldInstanceService.getOrMaterialize(WorldInstance.DEFAULT_ID));
        worldInstanceService.enterGame(observerConnection, character);

        characterDelete.onReceive(connection, "Hero");

        assertThat(connection.received).anyMatch(message -> message.equals(new CharacterCurrentlyInGame("Hero")));
        assertThat(characterDao.findByAccountIdAndWorldInstanceId(account.getId(), WorldInstance.DEFAULT_ID))
                .isPresent();
    }

    @Test
    void successfulDeletionSendsCharacterDeletedAndStatus() {
        RecordingConnection connection = enterCharSelect("p4");
        Account account = authWorld.account(connection);
        GamePlayer character = new GamePlayer(UUID.randomUUID(), account.getId(), "Hero", UUID.randomUUID(), Gender.MAN,
                Race.HUMAN, CharacterClass.FIGHTER, 1, 10, 10, TestAttributes.of(10, 10, 10, 10, 10, 10), 0, 0);
        character.setWorldInstanceId(WorldInstance.DEFAULT_ID);
        characterDao.insert(character);

        characterDelete.onReceive(connection, "Hero");

        assertThat(connection.received).anyMatch(message -> message.equals(new CharacterDeleted("Hero")));
        assertThat(connection.received).anyMatch(NoCharacterInWorld.class::isInstance);
        assertThat(characterDao.findByAccountIdAndWorldInstanceId(account.getId(), WorldInstance.DEFAULT_ID)).isEmpty();
    }

    private RecordingConnection enterCharSelect(String login) {
        roomService.warmRooms();
        Account account = new Account(UUID.randomUUID(), login, "hashed-password", null);
        accountDao.insert(account);
        RecordingConnection connection = new RecordingConnection();
        authWorld.enterWorld(connection, account);
        worldInstanceService.enterCharSelect(connection,
                worldInstanceService.getOrMaterialize(WorldInstance.DEFAULT_ID));
        connection.received.clear();
        return connection;
    }
}
