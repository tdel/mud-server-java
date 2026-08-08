package fr.idev.mudserver.controller.ingame;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import fr.idev.mudserver.AbstractIntegrationTest;
import fr.idev.mudserver.controller.RecordingConnection;
import fr.idev.mudserver.domain.Account;
import fr.idev.mudserver.domain.Room;
import fr.idev.mudserver.domain.actor.CharacterClass;
import fr.idev.mudserver.domain.actor.Gender;
import fr.idev.mudserver.domain.actor.GamePlayer;
import fr.idev.mudserver.domain.actor.Race;
import fr.idev.mudserver.domain.actor.TestAttributes;
import fr.idev.mudserver.domain.actor.TestProficiencies;
import fr.idev.mudserver.game.CheckResult;
import fr.idev.mudserver.game.GameWorld;
import fr.idev.mudserver.game.RoomService;
import fr.idev.mudserver.network.message.Usage;
import fr.idev.mudserver.network.message.ingame.CheckOutcome;
import fr.idev.mudserver.persistence.AccountDao;
import fr.idev.mudserver.persistence.CharacterDao;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class CheckTest extends AbstractIntegrationTest {

    @Autowired
    private Check check;

    @Autowired
    private GameWorld gameWorld;

    @Autowired
    private RoomService roomService;

    @Autowired
    private AccountDao accountDao;

    @Autowired
    private CharacterDao characterDao;

    @Test
    void fewerThanTwoTokensSendsUsage() {
        RecordingConnection connection = enterGame();

        check.onReceive(connection, "athletics");

        assertThat(connection.received).containsExactly(new Usage("check <skill> <dc>"));
    }

    @Test
    void unparsableDcSendsUsage() {
        RecordingConnection connection = enterGame();

        check.onReceive(connection, "athletics notanumber");

        assertThat(connection.received).containsExactly(new Usage("check <skill> <dc>"));
    }

    @Test
    void unknownSkillSendsUsage() {
        RecordingConnection connection = enterGame();

        check.onReceive(connection, "flying 10");

        assertThat(connection.received).containsExactly(new Usage("check <skill> <dc>"));
    }

    @Test
    void validSkillAndDcSendsCheckOutcomeWithTheCheckServiceResult() {
        RecordingConnection connection = enterGame();

        check.onReceive(connection, "athletics 10");

        assertThat(connection.received).hasSize(1);
        CheckResult result = ((CheckOutcome) connection.received.get(0)).result();
        assertThat(result.label()).isEqualTo("Athletics");
        assertThat(result.dc()).isEqualTo(10);
    }

    private RecordingConnection enterGame() {
        roomService.warmRooms();
        Room startingRoom = roomService.startingRoom().orElseThrow();
        Account account = new Account(UUID.randomUUID(), "verificateur-" + UUID.randomUUID(), "hashed-password", null);
        accountDao.insert(account);
        RecordingConnection connection = new RecordingConnection();
        GamePlayer character = new GamePlayer(UUID.randomUUID(), account.getId(), "Verificateur", startingRoom.getId(),
                Gender.MAN, Race.HUMAN, CharacterClass.FIGHTER, TestProficiencies.savingThrows(CharacterClass.FIGHTER),
                TestProficiencies.skills(CharacterClass.FIGHTER), 1, 10, 10, TestAttributes.of(10, 10, 10, 10, 10, 10),
                0, 0);
        characterDao.insert(character);
        gameWorld.enterWorld(connection, character);
        connection.received.clear();
        return connection;
    }
}
