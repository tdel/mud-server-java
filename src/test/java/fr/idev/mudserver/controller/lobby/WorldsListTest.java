package fr.idev.mudserver.controller.lobby;

import java.util.UUID;

import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import fr.idev.mudserver.AbstractIntegrationTest;
import fr.idev.mudserver.controller.RecordingConnection;
import fr.idev.mudserver.domain.Account;
import fr.idev.mudserver.domain.RoomInstance;
import fr.idev.mudserver.domain.TestRooms;
import fr.idev.mudserver.domain.WorldInstance;
import fr.idev.mudserver.domain.actor.CharacterClass;
import fr.idev.mudserver.domain.actor.Gender;
import fr.idev.mudserver.domain.actor.GamePlayer;
import fr.idev.mudserver.domain.actor.Race;
import fr.idev.mudserver.domain.actor.TestAttributes;
import fr.idev.mudserver.game.AuthWorld;
import fr.idev.mudserver.game.RoomService;
import fr.idev.mudserver.network.message.lobby.WorldsList.Entry;
import fr.idev.mudserver.persistence.AccountDao;
import fr.idev.mudserver.persistence.CharacterDao;

import static fr.idev.mudserver.persistence.jooq.Tables.WORLD_INSTANCE_MEMBER;
import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class WorldsListTest extends AbstractIntegrationTest {

    @Autowired
    private WorldsList worldsList;

    @Autowired
    private AuthWorld authWorld;

    @Autowired
    private RoomService roomService;

    @Autowired
    private AccountDao accountDao;

    @Autowired
    private CharacterDao characterDao;

    @Autowired
    private DSLContext dsl;

    @Test
    void listsTheDefaultWorldWithoutAnExistingCharacterHint() {
        RecordingConnection connection = enterLobby("p1");

        worldsList.onReceive(connection, "");

        Entry entry = defaultEntry(connection);
        assertThat(entry.shortName()).isEqualTo("default");
        assertThat(entry.existingCharacterName()).isNull();
    }

    @Test
    void listsTheDefaultWorldWithAnExistingCharacterHintWhenTheAccountIsAMember() {
        RecordingConnection connection = enterLobby("p2");
        Account account = connection.account();
        RoomInstance room = TestRooms.room(UUID.randomUUID(), "Test Room", "...");
        GamePlayer character = new GamePlayer(UUID.randomUUID(), account, "Hero", room, Gender.MAN, Race.HUMAN,
                CharacterClass.FIGHTER, 1, 10, 10, TestAttributes.of(10, 10, 10, 10, 10, 10), 0, 0);
        character.setWorldInstanceId(WorldInstance.DEFAULT_ID);
        characterDao.insert(character);
        dsl.insertInto(WORLD_INSTANCE_MEMBER, WORLD_INSTANCE_MEMBER.WORLD_INSTANCE_ID, WORLD_INSTANCE_MEMBER.ACCOUNT_ID)
                .values(WorldInstance.DEFAULT_ID, account.getId()).execute();

        worldsList.onReceive(connection, "");

        Entry entry = defaultEntry(connection);
        assertThat(entry.existingCharacterName()).isEqualTo("Hero");
        assertThat(entry.existingCharacterClass()).isEqualTo(CharacterClass.FIGHTER);
        assertThat(entry.existingCharacterLevel()).isEqualTo(1);
    }

    /**
     * Filtre sur {@code shortName} plutôt que d'exiger un {@code worlds()} de
     * taille 1 : {@code data/worlds/arena/} (fixture d'isolation, voir
     * {@code multi-world.md} Phase D) apparaît aussi dans la liste depuis cette
     * phase.
     */
    private Entry defaultEntry(RecordingConnection connection) {
        fr.idev.mudserver.network.message.lobby.WorldsList message = (fr.idev.mudserver.network.message.lobby.WorldsList) connection.received
                .get(0);
        return message.worlds().stream().filter(entry -> entry.shortName().equals("default")).findFirst().orElseThrow();
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
