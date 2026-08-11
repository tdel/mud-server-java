package fr.idev.mudserver.controller.lobby;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import fr.idev.mudserver.AbstractIntegrationTest;
import fr.idev.mudserver.controller.RecordingConnection;
import fr.idev.mudserver.domain.Account;
import fr.idev.mudserver.game.AuthWorld;
import fr.idev.mudserver.game.PartyService;
import fr.idev.mudserver.network.message.lobby.AlreadyInParty;
import fr.idev.mudserver.network.message.lobby.PartyCreated;
import fr.idev.mudserver.persistence.AccountDao;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class PartyCreateTest extends AbstractIntegrationTest {

    @Autowired
    private PartyCreate partyCreate;

    @Autowired
    private AuthWorld authWorld;

    @Autowired
    private PartyService partyService;

    @Autowired
    private AccountDao accountDao;

    @Test
    void createsAPartyWithCallerAsLeader() {
        RecordingConnection connection = enterLobby("pc-leader1");
        Account account = authWorld.account(connection);

        partyCreate.onReceive(connection, "");

        assertThat(connection.received).containsExactly(new PartyCreated());
        assertThat(partyService.partyOf(account.getId())).isPresent();
        assertThat(partyService.partyOf(account.getId()).get().isLeader(account.getId())).isTrue();
    }

    @Test
    void alreadyInAPartyRefusesToCreateAnother() {
        RecordingConnection connection = enterLobby("pc-leader2");
        partyCreate.onReceive(connection, "");
        connection.received.clear();

        partyCreate.onReceive(connection, "");

        assertThat(connection.received).containsExactly(new AlreadyInParty());
    }

    private RecordingConnection enterLobby(String login) {
        Account account = new Account(UUID.randomUUID(), login, "hashed-password", null);
        accountDao.insert(account);
        RecordingConnection connection = new RecordingConnection();
        authWorld.enterWorld(connection, account);
        connection.received.clear();
        return connection;
    }
}
