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
import fr.idev.mudserver.network.message.Usage;
import fr.idev.mudserver.network.message.lobby.AlreadyInAnotherParty;
import fr.idev.mudserver.network.message.lobby.CannotInviteSelf;
import fr.idev.mudserver.network.message.lobby.NotPartyLeader;
import fr.idev.mudserver.network.message.lobby.PartyInviteReceived;
import fr.idev.mudserver.network.message.lobby.PartyInviteSent;
import fr.idev.mudserver.network.message.lobby.PlayerNotOnline;
import fr.idev.mudserver.persistence.AccountDao;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class PartyInviteTest extends AbstractIntegrationTest {

    @Autowired
    private PartyInvite partyInvite;

    @Autowired
    private PartyCreate partyCreate;

    @Autowired
    private AuthWorld authWorld;

    @Autowired
    private PartyService partyService;

    @Autowired
    private AccountDao accountDao;

    @Test
    void emptyArgumentSendsUsage() {
        RecordingConnection leader = enterLobby("pi-leader1");
        partyCreate.onReceive(leader, "");
        leader.received.clear();

        partyInvite.onReceive(leader, "");

        assertThat(leader.received).containsExactly(new Usage("party-invite <login>"));
    }

    @Test
    void invitingSelfIsRefused() {
        RecordingConnection leader = enterLobby("pi-leader2");
        partyCreate.onReceive(leader, "");
        leader.received.clear();

        partyInvite.onReceive(leader, "pi-leader2");

        assertThat(leader.received).containsExactly(new CannotInviteSelf());
    }

    @Test
    void notInAPartyOrNotLeaderIsRefused() {
        RecordingConnection notLeader = enterLobby("pi-caller1");
        enterLobby("pi-target1");

        partyInvite.onReceive(notLeader, "pi-target1");

        assertThat(notLeader.received).containsExactly(new NotPartyLeader());
    }

    @Test
    void unknownOrOfflineTargetSendsPlayerNotOnline() {
        RecordingConnection leader = enterLobby("pi-leader3");
        partyCreate.onReceive(leader, "");
        leader.received.clear();

        partyInvite.onReceive(leader, "nobody");

        assertThat(leader.received).containsExactly(new PlayerNotOnline("nobody"));
    }

    @Test
    void targetAlreadyInAPartyIsRefused() {
        RecordingConnection leader = enterLobby("pi-leader4");
        partyCreate.onReceive(leader, "");
        leader.received.clear();
        RecordingConnection target = enterLobby("pi-target4");
        partyCreate.onReceive(target, "");

        partyInvite.onReceive(leader, "pi-target4");

        assertThat(leader.received).containsExactly(new AlreadyInAnotherParty("pi-target4"));
    }

    @Test
    void successfulInviteNotifiesBothConnections() {
        RecordingConnection leader = enterLobby("pi-leader5");
        partyCreate.onReceive(leader, "");
        leader.received.clear();
        RecordingConnection target = enterLobby("pi-target5");
        Account targetAccount = target.account();

        partyInvite.onReceive(leader, "pi-target5");

        assertThat(leader.received).containsExactly(new PartyInviteSent("pi-target5"));
        assertThat(target.received).containsExactly(new PartyInviteReceived("pi-leader5"));
        Account leaderAccount = leader.account();
        assertThat(partyService.pendingInviteFor(targetAccount.getId())).isPresent();
        assertThat(partyService.pendingInviteFor(targetAccount.getId()).get().isLeader(leaderAccount.getId())).isTrue();
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
