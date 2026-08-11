package fr.idev.mudserver.controller.charselect;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import fr.idev.mudserver.AbstractIntegrationTest;
import fr.idev.mudserver.controller.RecordingConnection;
import fr.idev.mudserver.controller.lobby.PartyAccept;
import fr.idev.mudserver.controller.lobby.PartyCreate;
import fr.idev.mudserver.controller.lobby.PartyInvite;
import fr.idev.mudserver.controller.lobby.WorldEnter;
import fr.idev.mudserver.domain.Account;
import fr.idev.mudserver.domain.RoomInstance;
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
    private WorldEnter worldEnter;

    @Autowired
    private PartyCreate partyCreate;

    @Autowired
    private PartyInvite partyInvite;

    @Autowired
    private PartyAccept partyAccept;

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
        assertThat(connection.character().getName()).isEqualTo("Hero");
    }

    /**
     * Preuve de régression pour {@code RoomService.spawnCharacter} : avant
     * correction, cette méthode matérialisait toujours
     * {@link WorldInstance#DEFAULT_ID} en dur au login, quelle que soit l'instance
     * réelle du personnage — un personnage de l'instance "arena" (créée en lançant
     * une party via {@link WorldEnter}) se serait retrouvé spawné dans les rooms de
     * l'instance par défaut.
     */
    @Test
    void existingCharacterInANonDefaultInstanceSpawnsIntoThatInstanceNotTheDefaultOne() {
        roomService.warmRooms();
        RecordingConnection leader = enterLobby("p-arena-leader");
        partyCreate.onReceive(leader, "");
        RecordingConnection member = enterLobby("p-arena-member");
        partyInvite.onReceive(leader, "p-arena-member");
        partyAccept.onReceive(member, "");
        Account account = authWorld.account(leader);

        worldEnter.onReceive(leader, "arena");
        WorldInstance arenaInstance = worldInstanceService.worldInstanceOf(leader);
        assertThat(arenaInstance.getId()).isNotEqualTo(WorldInstance.DEFAULT_ID);

        RoomInstance arenaStartingRoom = arenaInstance.startingRoomInstance().orElseThrow();
        GamePlayer character = new GamePlayer(UUID.randomUUID(), account.getId(), "Gladiator",
                arenaStartingRoom.getTemplateId(), Gender.MAN, Race.HUMAN, CharacterClass.FIGHTER, 1, 10, 10,
                TestAttributes.of(10, 10, 10, 10, 10, 10), 0, 0);
        character.setWorldInstanceId(arenaInstance.getId());
        characterDao.insert(character);
        leader.received.clear();

        characterSelect.onReceive(leader, "");

        assertThat(leader.state()).isEqualTo(ConnectionState.INGAME);
        GamePlayer loaded = leader.character();
        assertThat(loaded.getWorldInstance().getId()).isEqualTo(arenaInstance.getId());
        assertThat(loaded.getCurrentRoom().getWorldInstanceId()).isEqualTo(arenaInstance.getId());

        RoomInstance defaultStartingRoom = worldInstanceService.getOrMaterialize(WorldInstance.DEFAULT_ID)
                .startingRoomInstance().orElseThrow();
        assertThat(loaded.getCurrentRoom()).isNotEqualTo(defaultStartingRoom);
    }

    private RecordingConnection enterLobby(String login) {
        Account account = new Account(UUID.randomUUID(), login, "hashed-password", null);
        accountDao.insert(account);
        RecordingConnection connection = new RecordingConnection();
        authWorld.enterWorld(connection, account);
        connection.received.clear();
        return connection;
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
