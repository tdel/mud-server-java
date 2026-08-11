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
import fr.idev.mudserver.network.message.lobby.NoPendingInvite;
import fr.idev.mudserver.network.message.lobby.PartyJoined;
import fr.idev.mudserver.network.message.lobby.PartyMemberJoined;
import fr.idev.mudserver.persistence.AccountDao;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class PartyAcceptTest extends AbstractIntegrationTest {

    @Autowired
    private PartyAccept partyAccept;

    @Autowired
    private PartyCreate partyCreate;

    @Autowired
    private PartyInvite partyInvite;

    @Autowired
    private AuthWorld authWorld;

    @Autowired
    private PartyService partyService;

    @Autowired
    private AccountDao accountDao;

    @Test
    void noPendingInviteIsRefused() {
        RecordingConnection connection = enterLobby("pa-p1");

        partyAccept.onReceive(connection, "");

        assertThat(connection.received).containsExactly(new NoPendingInvite());
    }

    @Test
    void acceptingJoinsThePartyAndNotifiesTheLeader() {
        RecordingConnection leader = enterLobby("pa-leader1");
        partyCreate.onReceive(leader, "");
        RecordingConnection invitee = enterLobby("pa-invitee1");
        partyInvite.onReceive(leader, "pa-invitee1");
        leader.received.clear();
        invitee.received.clear();

        partyAccept.onReceive(invitee, "");

        assertThat(invitee.received).containsExactly(new PartyJoined("pa-leader1", 2));
        assertThat(leader.received).containsExactly(new PartyMemberJoined("pa-invitee1"));
        Account leaderAccount = leader.account();
        assertThat(partyService.partyOf(leaderAccount.getId()).get().size()).isEqualTo(2);
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
