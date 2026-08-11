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
import fr.idev.mudserver.game.CharacterSelectionWorld;
import fr.idev.mudserver.game.GameWorld;
import fr.idev.mudserver.game.RoomService;
import fr.idev.mudserver.game.WorldInstanceService;
import fr.idev.mudserver.network.ConnectionState;
import fr.idev.mudserver.network.message.charselect.NoCharacterInWorld;
import fr.idev.mudserver.network.message.charselect.NowPlaying;
import fr.idev.mudserver.network.message.ingame.RoomDescription;
import fr.idev.mudserver.persistence.AccountDao;
import fr.idev.mudserver.persistence.CharacterDao;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class CharacterSelectTest extends AbstractIntegrationTest {

    @Autowired
    private CharacterSelect characterSelect;

    @Autowired
    private AuthWorld authWorld;

    @Autowired
    private CharacterSelectionWorld characterSelectionWorld;

    @Autowired
    private WorldInstanceService worldInstanceService;

    @Autowired
    private GameWorld gameWorld;

    @Autowired
    private RoomService roomService;

    @Autowired
    private AccountDao accountDao;

    @Autowired
    private CharacterDao characterDao;

    @Test
    void noCharacterYetShowsNoCharacterInWorld() {
        RecordingConnection connection = enterCharSelect("p1");

        characterSelect.onReceive(connection, "");

        assertThat(connection.received).anyMatch(NoCharacterInWorld.class::isInstance);
        assertThat(connection.state()).isEqualTo(ConnectionState.CHARSELECT);
    }

    @Test
    void existingCharacterMovesToIngameAndSendsNowPlayingThenLook() {
        RecordingConnection connection = enterCharSelect("p2");
        Account account = authWorld.account(connection);
        GamePlayer character = new GamePlayer(UUID.randomUUID(), account.getId(), "Hero", UUID.randomUUID(), Gender.MAN,
                Race.HUMAN, CharacterClass.FIGHTER, 1, 10, 10, TestAttributes.of(10, 10, 10, 10, 10, 10), 0, 0);
        character.setWorldInstanceId(WorldInstance.DEFAULT_ID);
        characterDao.insert(character);

        characterSelect.onReceive(connection, "");

        assertThat(connection.state()).isEqualTo(ConnectionState.INGAME);
        assertThat(connection.received).anyMatch(message -> message.equals(new NowPlaying("Hero")));
        assertThat(connection.received).anyMatch(RoomDescription.class::isInstance);
        assertThat(gameWorld.character(connection).getName()).isEqualTo("Hero");
    }

    private RecordingConnection enterCharSelect(String login) {
        roomService.warmRooms();
        Account account = new Account(UUID.randomUUID(), login, "hashed-password", null);
        accountDao.insert(account);
        RecordingConnection connection = new RecordingConnection();
        authWorld.enterWorld(connection, account);
        characterSelectionWorld.enterWorld(connection, worldInstanceService.getOrMaterialize(WorldInstance.DEFAULT_ID));
        connection.received.clear();
        return connection;
    }
}
