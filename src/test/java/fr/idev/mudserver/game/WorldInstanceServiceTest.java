package fr.idev.mudserver.game;

import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import fr.idev.mudserver.AbstractIntegrationTest;
import fr.idev.mudserver.controller.RecordingConnection;
import fr.idev.mudserver.domain.Account;
import fr.idev.mudserver.domain.RoomInstance;
import fr.idev.mudserver.domain.WorldInstance;
import fr.idev.mudserver.domain.WorldTemplate;
import fr.idev.mudserver.domain.actor.CharacterClass;
import fr.idev.mudserver.domain.actor.Gender;
import fr.idev.mudserver.domain.actor.GamePlayer;
import fr.idev.mudserver.domain.actor.Race;
import fr.idev.mudserver.domain.actor.TestAttributes;
import fr.idev.mudserver.network.ConnectionState;
import fr.idev.mudserver.persistence.AccountDao;
import fr.idev.mudserver.persistence.CharacterDao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Couvre spécifiquement le suivi CHARSELECT/INGAME et la règle de destruction
 * qu'{@link AuthWorld} portait avant d'être recentrée sur le seul compte — voir
 * sa Javadoc de classe.
 */
@Transactional
class WorldInstanceServiceTest extends AbstractIntegrationTest {

    @Autowired
    private WorldInstanceService worldInstanceService;

    @Autowired
    private WorldTemplateService worldTemplateService;

    @Autowired
    private RoomService roomService;

    @Autowired
    private AccountDao accountDao;

    @Autowired
    private CharacterDao characterDao;

    @Test
    void lastPlayerLeavingEvictsTheInstanceFromMemoryButNotFromDb() {
        roomService.warmRooms();
        WorldTemplate template = worldTemplateService.findByShortName("default").orElseThrow();
        Account account = new Account(UUID.randomUUID(), "wis-solo-" + UUID.randomUUID(), "hashed-password", null);
        accountDao.insert(account);

        WorldInstance instance = worldInstanceService.createInstance(template, Set.of(account.getId()),
                account.getId());
        RoomInstance startingRoomBeforeEviction = instance.startingRoomInstance().orElseThrow();

        GamePlayer character = new GamePlayer(UUID.randomUUID(), account, "Solitaire", startingRoomBeforeEviction,
                Gender.MAN, Race.HUMAN, CharacterClass.FIGHTER, 1, 10, 10, TestAttributes.of(10, 10, 10, 10, 10, 10), 0,
                0);
        character.setWorldInstanceId(instance.getId());
        characterDao.insert(character);
        RecordingConnection connection = new RecordingConnection();
        worldInstanceService.enterCharSelect(connection, instance);
        worldInstanceService.enterGame(connection, character);

        assertThat(instance.onlineCharacters()).containsExactly(character);

        worldInstanceService.exitGame(connection);

        assertThat(instance.onlineCharacters()).isEmpty();
        WorldInstance rematerialized = worldInstanceService.getOrMaterialize(instance.getId());
        RoomInstance startingRoomAfterEviction = rematerialized.startingRoomInstance().orElseThrow();
        assertThat(startingRoomAfterEviction).isNotSameAs(startingRoomBeforeEviction);
    }

    @Test
    void enterCharSelectThenExitCharSelectRoundTripsThroughCharselectAndFindsTheExistingCharacter() {
        roomService.warmRooms();
        WorldInstance instance = worldInstanceService.getOrMaterialize(WorldInstance.DEFAULT_ID);
        Account account = new Account(UUID.randomUUID(), "wis-round-trip-" + UUID.randomUUID(), "hashed-password",
                null);
        accountDao.insert(account);
        RoomInstance startingRoom = instance.startingRoomInstance().orElseThrow();
        GamePlayer character = new GamePlayer(UUID.randomUUID(), account, "AllerRetour", startingRoom, Gender.MAN,
                Race.HUMAN, CharacterClass.FIGHTER, 1, 10, 10, TestAttributes.of(10, 10, 10, 10, 10, 10), 0, 0);
        character.setWorldInstanceId(instance.getId());
        characterDao.insert(character);
        RecordingConnection connection = new RecordingConnection();

        worldInstanceService.enterCharSelect(connection, instance);

        assertThat(connection.state()).isEqualTo(ConnectionState.CHARSELECT);
        assertThat(connection.worldInstance()).isEqualTo(instance);
        assertThat(worldInstanceService.findCharacterFor(account, instance)).contains(character);

        worldInstanceService.exitCharSelect(connection);

        assertThat(connection.state()).isEqualTo(ConnectionState.LOBBY);
        assertThatThrownBy(connection::worldInstance).isInstanceOf(IllegalStateException.class);
    }
}
