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
import fr.idev.mudserver.network.message.lobby.NewPartyLeader;
import fr.idev.mudserver.network.message.lobby.NotInParty;
import fr.idev.mudserver.network.message.lobby.PartyLeft;
import fr.idev.mudserver.network.message.lobby.PartyMemberLeft;
import fr.idev.mudserver.persistence.AccountDao;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class PartyLeaveTest extends AbstractIntegrationTest {

    @Autowired
    private PartyLeave partyLeave;

    @Autowired
    private PartyCreate partyCreate;

    @Autowired
    private PartyInvite partyInvite;

    @Autowired
    private PartyAccept partyAccept;

    @Autowired
    private AuthWorld authWorld;

    @Autowired
    private PartyService partyService;

    @Autowired
    private AccountDao accountDao;

    @Test
    void notInAPartyIsRefused() {
        RecordingConnection connection = enterLobby("pl-p1");

        partyLeave.onReceive(connection, "");

        assertThat(connection.received).containsExactly(new NotInParty());
    }

    @Test
    void memberLeavingKeepsTheSameLeader() {
        RecordingConnection leader = enterLobby("pl-leader1");
        partyCreate.onReceive(leader, "");
        RecordingConnection member = enterLobby("pl-member1");
        partyInvite.onReceive(leader, "pl-member1");
        partyAccept.onReceive(member, "");
        leader.received.clear();
        member.received.clear();

        partyLeave.onReceive(member, "");

        assertThat(member.received).containsExactly(new PartyLeft());
        assertThat(leader.received).containsExactly(new PartyMemberLeft("pl-member1"));
        assertThat(leader.received).noneMatch(NewPartyLeader.class::isInstance);
    }

    @Test
    void leaderLeavingPromotesTheNextMember() {
        RecordingConnection leader = enterLobby("pl-leader2");
        partyCreate.onReceive(leader, "");
        RecordingConnection member = enterLobby("pl-member2");
        partyInvite.onReceive(leader, "pl-member2");
        partyAccept.onReceive(member, "");
        leader.received.clear();
        member.received.clear();
        Account memberAccount = member.account();

        partyLeave.onReceive(leader, "");

        assertThat(member.received).contains(new PartyMemberLeft("pl-leader2"), new NewPartyLeader("pl-member2"));
        assertThat(partyService.partyOf(memberAccount.getId()).get().isLeader(memberAccount.getId())).isTrue();
    }

    @Test
    void soleMemberLeavingDissolvesTheParty() {
        RecordingConnection leader = enterLobby("pl-leader3");
        partyCreate.onReceive(leader, "");
        Account leaderAccount = leader.account();

        partyLeave.onReceive(leader, "");

        assertThat(partyService.partyOf(leaderAccount.getId())).isEmpty();
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
