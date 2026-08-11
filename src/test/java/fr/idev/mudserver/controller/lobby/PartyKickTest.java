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
import fr.idev.mudserver.network.message.lobby.CannotKickSelf;
import fr.idev.mudserver.network.message.lobby.NoSuchPartyMember;
import fr.idev.mudserver.network.message.lobby.NotPartyLeader;
import fr.idev.mudserver.network.message.lobby.PartyKicked;
import fr.idev.mudserver.network.message.lobby.PartyMemberKicked;
import fr.idev.mudserver.network.message.lobby.PartyMemberLeft;
import fr.idev.mudserver.persistence.AccountDao;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class PartyKickTest extends AbstractIntegrationTest {

    @Autowired
    private PartyKick partyKick;

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
    void emptyArgumentSendsUsage() {
        RecordingConnection leader = enterLobby("pk-leader1");
        partyCreate.onReceive(leader, "");
        leader.received.clear();

        partyKick.onReceive(leader, "");

        assertThat(leader.received).containsExactly(new Usage("party-kick <login>"));
    }

    @Test
    void kickingSelfIsRefused() {
        RecordingConnection leader = enterLobby("pk-leader2");
        partyCreate.onReceive(leader, "");
        leader.received.clear();

        partyKick.onReceive(leader, "pk-leader2");

        assertThat(leader.received).containsExactly(new CannotKickSelf());
    }

    @Test
    void nonLeaderCannotKick() {
        RecordingConnection notLeader = enterLobby("pk-caller1");

        partyKick.onReceive(notLeader, "someone");

        assertThat(notLeader.received).containsExactly(new NotPartyLeader());
    }

    @Test
    void unknownOrNonMemberTargetSendsNoSuchPartyMember() {
        RecordingConnection leader = enterLobby("pk-leader3");
        partyCreate.onReceive(leader, "");
        leader.received.clear();

        partyKick.onReceive(leader, "nobody");

        assertThat(leader.received).containsExactly(new NoSuchPartyMember("nobody"));
    }

    @Test
    void kickingAConnectedMemberNotifiesEveryone() {
        RecordingConnection leader = enterLobby("pk-leader4");
        partyCreate.onReceive(leader, "");
        RecordingConnection member = enterLobby("pk-member4");
        partyInvite.onReceive(leader, "pk-member4");
        partyAccept.onReceive(member, "");
        leader.received.clear();
        member.received.clear();

        partyKick.onReceive(leader, "pk-member4");

        assertThat(leader.received).containsExactly(new PartyKicked("pk-member4"));
        assertThat(member.received).containsExactly(new PartyMemberKicked());
        Account leaderAccount = leader.account();
        assertThat(partyService.partyOf(leaderAccount.getId()).get().size()).isEqualTo(1);
    }

    @Test
    void kickingAnOfflineMemberStillRemovesThem() {
        RecordingConnection leader = enterLobby("pk-leader5");
        partyCreate.onReceive(leader, "");
        RecordingConnection member = enterLobby("pk-member5");
        Account memberAccount = member.account();
        partyInvite.onReceive(leader, "pk-member5");
        partyAccept.onReceive(member, "");
        authWorld.exitWorld(member);
        leader.received.clear();

        partyKick.onReceive(leader, "pk-member5");

        assertThat(leader.received).containsExactly(new PartyKicked("pk-member5"));
        Account leaderAccount = leader.account();
        assertThat(partyService.partyOf(leaderAccount.getId()).get().isMember(memberAccount.getId())).isFalse();
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
