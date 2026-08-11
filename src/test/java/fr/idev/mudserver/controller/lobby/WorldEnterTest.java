package fr.idev.mudserver.controller.lobby;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import fr.idev.mudserver.AbstractIntegrationTest;
import fr.idev.mudserver.controller.RecordingConnection;
import fr.idev.mudserver.domain.Account;
import fr.idev.mudserver.domain.RoomInstance;
import fr.idev.mudserver.domain.WorldInstance;
import fr.idev.mudserver.game.AuthWorld;
import fr.idev.mudserver.game.PartyService;
import fr.idev.mudserver.game.RoomService;
import fr.idev.mudserver.game.WorldInstanceService;
import fr.idev.mudserver.network.ConnectionState;
import fr.idev.mudserver.network.message.Usage;
import fr.idev.mudserver.network.message.charselect.NoCharacterInWorld;
import fr.idev.mudserver.network.message.lobby.MemberOffline;
import fr.idev.mudserver.network.message.lobby.NoWorldNamed;
import fr.idev.mudserver.network.message.lobby.NotEnoughPlayers;
import fr.idev.mudserver.network.message.lobby.NotPartyLeader;
import fr.idev.mudserver.persistence.AccountDao;
import fr.idev.mudserver.persistence.WorldInstanceDao;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class WorldEnterTest extends AbstractIntegrationTest {

    @Autowired
    private WorldEnter worldEnter;

    @Autowired
    private PartyCreate partyCreate;

    @Autowired
    private PartyInvite partyInvite;

    @Autowired
    private PartyAccept partyAccept;

    @Autowired
    private PartyService partyService;

    @Autowired
    private AuthWorld authWorld;

    @Autowired
    private WorldInstanceService worldInstanceService;

    @Autowired
    private RoomService roomService;

    @Autowired
    private AccountDao accountDao;

    @Autowired
    private WorldInstanceDao worldInstanceDao;

    @Test
    void emptyArgumentSendsUsage() {
        RecordingConnection connection = enterLobby("p1");

        worldEnter.onReceive(connection, "");

        assertThat(connection.received).containsExactly(new Usage("world-enter <short-name>"));
    }

    @Test
    void unknownShortNameSendsNoWorldNamed() {
        RecordingConnection connection = enterLobby("p2");

        worldEnter.onReceive(connection, "nowhere");

        assertThat(connection.received).containsExactly(new NoWorldNamed("nowhere"));
        assertThat(connection.state()).isEqualTo(ConnectionState.LOBBY);
    }

    @Test
    void knownShortNameEntersCharselectAndShowsNoCharacterYet() {
        RecordingConnection connection = enterLobby("p3");

        worldEnter.onReceive(connection, "default");

        assertThat(connection.state()).isEqualTo(ConnectionState.CHARSELECT);
        assertThat(connection.received).anyMatch(NoCharacterInWorld.class::isInstance);
        assertThat(worldInstanceService.worldInstanceOf(connection)).isNotNull();
    }

    @Test
    void soloWorldEnterReusesTheSameAccountsExistingInstanceOnASecondCall() {
        RecordingConnection connection = enterLobby("we-solo-reuse");

        worldEnter.onReceive(connection, "default");
        WorldInstance firstInstance = worldInstanceService.worldInstanceOf(connection);
        worldInstanceService.exitCharSelect(connection);

        worldEnter.onReceive(connection, "default");
        WorldInstance secondInstance = worldInstanceService.worldInstanceOf(connection);

        assertThat(secondInstance.getId()).isEqualTo(firstInstance.getId());
    }

    @Test
    void soloWorldEnterCreatesDistinctInstancesForDifferentAccounts() {
        RecordingConnection alice = enterLobby("we-solo-alice");
        RecordingConnection bob = enterLobby("we-solo-bob");

        worldEnter.onReceive(alice, "default");
        worldEnter.onReceive(bob, "default");

        WorldInstance aliceInstance = worldInstanceService.worldInstanceOf(alice);
        WorldInstance bobInstance = worldInstanceService.worldInstanceOf(bob);

        assertThat(aliceInstance.getId()).isNotEqualTo(bobInstance.getId());
    }

    @Test
    void nonLeaderPartyMemberCannotWorldEnter() {
        RecordingConnection leader = enterLobby("we-leader1");
        partyCreate.onReceive(leader, "");
        RecordingConnection member = enterLobby("we-member1");
        partyInvite.onReceive(leader, "we-member1");
        partyAccept.onReceive(member, "");
        member.received.clear();

        worldEnter.onReceive(member, "arena");

        assertThat(member.received).containsExactly(new NotPartyLeader());
    }

    @Test
    void tooFewPartyMembersForTemplateMinPlayersIsRefused() {
        RecordingConnection leader = enterLobby("we-leader2");
        partyCreate.onReceive(leader, "");
        leader.received.clear();

        worldEnter.onReceive(leader, "arena");

        assertThat(leader.received).containsExactly(new NotEnoughPlayers(2, 1));
    }

    @Test
    void partyMemberNotInLobbyBlocksLaunch() {
        RecordingConnection leader = enterLobby("we-leader3");
        partyCreate.onReceive(leader, "");
        RecordingConnection member = enterLobby("we-member3");
        partyInvite.onReceive(leader, "we-member3");
        partyAccept.onReceive(member, "");
        authWorld.exitWorld(member);
        leader.received.clear();

        worldEnter.onReceive(leader, "arena");

        assertThat(leader.received).containsExactly(new MemberOffline("we-member3"));
    }

    @Test
    void successfulPartyLaunchMovesEveryMemberToTheSameFreshInstance() {
        RecordingConnection leader = enterLobby("we-leader4");
        partyCreate.onReceive(leader, "");
        RecordingConnection member = enterLobby("we-member4");
        partyInvite.onReceive(leader, "we-member4");
        partyAccept.onReceive(member, "");
        Account leaderAccount = authWorld.account(leader);

        worldEnter.onReceive(leader, "arena");

        assertThat(leader.state()).isEqualTo(ConnectionState.CHARSELECT);
        assertThat(member.state()).isEqualTo(ConnectionState.CHARSELECT);
        WorldInstance leaderInstance = worldInstanceService.worldInstanceOf(leader);
        WorldInstance memberInstance = worldInstanceService.worldInstanceOf(member);
        assertThat(leaderInstance.getId()).isEqualTo(memberInstance.getId());
        assertThat(worldInstanceDao.findById(leaderInstance.getId())).isPresent();
        assertThat(partyService.partyOf(leaderAccount.getId())).isEmpty();
    }

    @Test
    void twoDifferentPartiesLaunchingTheSameTemplateGetIsolatedInstances() {
        RecordingConnection leaderA = enterLobby("we-leaderA");
        partyCreate.onReceive(leaderA, "");
        RecordingConnection memberA = enterLobby("we-memberA");
        partyInvite.onReceive(leaderA, "we-memberA");
        partyAccept.onReceive(memberA, "");
        worldEnter.onReceive(leaderA, "arena");

        RecordingConnection leaderB = enterLobby("we-leaderB");
        partyCreate.onReceive(leaderB, "");
        RecordingConnection memberB = enterLobby("we-memberB");
        partyInvite.onReceive(leaderB, "we-memberB");
        partyAccept.onReceive(memberB, "");
        worldEnter.onReceive(leaderB, "arena");

        WorldInstance instanceA = worldInstanceService.worldInstanceOf(leaderA);
        WorldInstance instanceB = worldInstanceService.worldInstanceOf(leaderB);
        assertThat(instanceA.getId()).isNotEqualTo(instanceB.getId());

        RoomInstance startingRoomA = instanceA.startingRoomInstance().orElseThrow();
        RoomInstance startingRoomB = instanceB.startingRoomInstance().orElseThrow();
        assertThat(startingRoomA).isNotSameAs(startingRoomB);
        assertThat(startingRoomA.getId()).isNotEqualTo(startingRoomB.getId());
    }

    private RecordingConnection enterLobby(String login) {
        roomService.warmRooms();
        Account account = new Account(UUID.randomUUID(), login, "hashed-password", null);
        accountDao.insert(account);
        RecordingConnection connection = new RecordingConnection();
        authWorld.enterWorld(connection, account);
        connection.received.clear();
        return connection;
    }
}
