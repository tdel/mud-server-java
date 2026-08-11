package fr.idev.mudserver.controller;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import fr.idev.mudserver.AbstractIntegrationTest;
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
import fr.idev.mudserver.network.ConnectionState;
import fr.idev.mudserver.network.message.LoggedOut;
import fr.idev.mudserver.network.message.charselect.StoppedPlaying;
import fr.idev.mudserver.network.message.lobby.BackInLobby;
import fr.idev.mudserver.persistence.AccountDao;
import fr.idev.mudserver.persistence.CharacterDao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Transactional
class LogoutTest extends AbstractIntegrationTest {

    @Autowired
    private Logout logout;

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
    void fromIngameReturnsDirectlyToLobbyKeepingTheAccountRegistered() {
        roomService.warmRooms();
        Account account = new Account(UUID.randomUUID(), "p1", "hashed-password", null);
        accountDao.insert(account);
        GamePlayer character = new GamePlayer(UUID.randomUUID(), account.getId(), "Hero", UUID.randomUUID(), Gender.MAN,
                Race.HUMAN, CharacterClass.FIGHTER, 1, 10, 10, TestAttributes.of(10, 10, 10, 10, 10, 10), 0, 0);
        character.setWorldInstanceId(WorldInstance.DEFAULT_ID);
        characterDao.insert(character);
        RecordingConnection connection = new RecordingConnection();
        // Chemin réaliste : AuthWorld reste enregistré tout du long, y compris en
        // INGAME (voir sa Javadoc) — Logout s'appuie dessus plutôt que de recharger le
        // compte via AccountDao.
        authWorld.enterWorld(connection, account);
        worldInstanceService.enterCharSelect(connection,
                worldInstanceService.getOrMaterialize(WorldInstance.DEFAULT_ID));
        worldInstanceService.enterGame(connection, character);

        logout.onReceive(connection, "");

        assertThat(connection.state()).isEqualTo(ConnectionState.LOBBY);
        assertThat(connection.received).contains(new StoppedPlaying("Hero"), new BackInLobby());
        assertThat(connection.account().getId()).isEqualTo(account.getId());
        assertThatThrownBy(connection::worldInstance).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(connection::character).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void fromCharselectReturnsToLobby() {
        roomService.warmRooms();
        Account account = new Account(UUID.randomUUID(), "p2", "hashed-password", null);
        accountDao.insert(account);
        RecordingConnection connection = new RecordingConnection();
        authWorld.enterWorld(connection, account);
        worldInstanceService.enterCharSelect(connection,
                worldInstanceService.getOrMaterialize(WorldInstance.DEFAULT_ID));
        connection.received.clear();

        logout.onReceive(connection, "");

        assertThat(connection.state()).isEqualTo(ConnectionState.LOBBY);
        assertThat(connection.received).containsExactly(new BackInLobby());
        assertThatThrownBy(connection::worldInstance).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void fromLobbyReturnsToConnected() {
        Account account = new Account(UUID.randomUUID(), "p3", "hashed-password", null);
        accountDao.insert(account);
        RecordingConnection connection = new RecordingConnection();
        authWorld.enterWorld(connection, account);
        connection.received.clear();

        logout.onReceive(connection, "");

        assertThat(connection.state()).isEqualTo(ConnectionState.CONNECTED);
        assertThat(connection.received).containsExactly(new LoggedOut());
        assertThatThrownBy(connection::account).isInstanceOf(IllegalStateException.class);
    }
}
