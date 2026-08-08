package fr.idev.mudserver.controller.authed;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import fr.idev.mudserver.AbstractIntegrationTest;
import fr.idev.mudserver.controller.RecordingConnection;
import fr.idev.mudserver.domain.Account;
import fr.idev.mudserver.domain.actor.CharacterClass;
import fr.idev.mudserver.domain.actor.Gender;
import fr.idev.mudserver.domain.actor.GamePlayer;
import fr.idev.mudserver.domain.actor.Race;
import fr.idev.mudserver.domain.actor.TestAttributes;
import fr.idev.mudserver.domain.actor.TestProficiencies;
import fr.idev.mudserver.game.AuthWorld;
import fr.idev.mudserver.game.GameWorld;
import fr.idev.mudserver.game.RoomService;
import fr.idev.mudserver.game.actor.ClassService;
import fr.idev.mudserver.game.actor.RaceService;
import fr.idev.mudserver.network.message.Usage;
import fr.idev.mudserver.network.message.authed.CharacterAlreadyExists;
import fr.idev.mudserver.network.message.authed.CharacterCreated;
import fr.idev.mudserver.network.message.authed.ChooseClass;
import fr.idev.mudserver.network.message.authed.ChooseGender;
import fr.idev.mudserver.network.message.authed.ChooseRace;
import fr.idev.mudserver.network.message.authed.InvalidClass;
import fr.idev.mudserver.network.message.authed.InvalidGender;
import fr.idev.mudserver.network.message.authed.InvalidRace;
import fr.idev.mudserver.network.message.ingame.GamePlayerStats;
import fr.idev.mudserver.persistence.AccountDao;
import fr.idev.mudserver.persistence.CharacterDao;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link RecordingConnection#queueAnswer} pilote les 3 prompts enchaînés
 * (genre, race, classe) : chaque réponse mise en file est consommée par le
 * {@code requestBlocking} suivant dès qu'il est émis, y compris à travers la
 * récursion de {@code CharacterCreate.promptGender/promptRace/promptClass} —
 * une file vide arrête simplement la chaîne au prompt courant, ce qui permet de
 * tester un rejet à n'importe quelle étape sans dérouler tout le scénario.
 */
@Transactional
class CharacterCreateTest extends AbstractIntegrationTest {

    @Autowired
    private CharacterCreate characterCreate;

    @Autowired
    private AuthWorld authWorld;

    @Autowired
    private GameWorld gameWorld;

    @Autowired
    private RaceService raceService;

    @Autowired
    private ClassService classService;

    @Autowired
    private RoomService roomService;

    @Autowired
    private AccountDao accountDao;

    @Autowired
    private CharacterDao characterDao;

    @Test
    void emptyNameSendsUsageAndRelistsCharacters() {
        RecordingConnection connection = enterAuthed("p1");

        characterCreate.onReceive(connection, "");

        assertThat(connection.received).anyMatch(message -> message.equals(new Usage("character-create <name>")));
        assertThat(connection.received)
                .anyMatch(fr.idev.mudserver.network.message.authed.CharacterList.class::isInstance);
    }

    @Test
    void nameAlreadyTakenSendsCharacterAlreadyExistsAndRelists() {
        RecordingConnection connection = enterAuthed("p2");
        Account account = authWorld.account(connection);
        GamePlayer existing = new GamePlayer(UUID.randomUUID(), account.getId(), "Existing", UUID.randomUUID(),
                Gender.MAN, Race.HUMAN, CharacterClass.FIGHTER, TestProficiencies.savingThrows(CharacterClass.FIGHTER),
                TestProficiencies.skills(CharacterClass.FIGHTER), 1, 10, 10, TestAttributes.of(10, 10, 10, 10, 10, 10),
                0, 0);
        characterDao.insert(existing);

        characterCreate.onReceive(connection, "Existing");

        assertThat(connection.received.get(0)).isEqualTo(new CharacterAlreadyExists("Existing"));
        assertThat(connection.received)
                .anyMatch(fr.idev.mudserver.network.message.authed.CharacterList.class::isInstance);
    }

    @Test
    void invalidGenderSendsInvalidGenderAndRePromptsGenderStep() {
        RecordingConnection connection = enterAuthed("p3");
        connection.queueAnswer("not-a-gender");

        characterCreate.onReceive(connection, "Hero");

        assertThat(connection.received).extracting(Object::getClass).containsSubsequence(ChooseGender.class,
                InvalidGender.class, ChooseGender.class);
        assertThat(connection.received).anyMatch(message -> message.equals(new InvalidGender("not-a-gender")));
    }

    @Test
    void invalidRaceSendsInvalidRaceAndRePromptsRaceStep() {
        RecordingConnection connection = enterAuthed("p4");
        connection.queueAnswer("man");
        connection.queueAnswer("not-a-race");

        characterCreate.onReceive(connection, "Hero");

        assertThat(connection.received).extracting(Object::getClass).containsSubsequence(ChooseGender.class,
                ChooseRace.class, InvalidRace.class, ChooseRace.class);
        assertThat(connection.received).anyMatch(message -> message.equals(new InvalidRace("not-a-race")));
    }

    @Test
    void invalidClassSendsInvalidClassAndRePromptsClassStep() {
        RecordingConnection connection = enterAuthed("p5");
        connection.queueAnswer("man");
        connection.queueAnswer("human");
        connection.queueAnswer("not-a-class");

        characterCreate.onReceive(connection, "Hero");

        assertThat(connection.received).extracting(Object::getClass).containsSubsequence(ChooseGender.class,
                ChooseRace.class, ChooseClass.class, InvalidClass.class, ChooseClass.class);
        assertThat(connection.received).anyMatch(message -> message.equals(new InvalidClass("not-a-class")));
    }

    @Test
    void genderRaceClassTokensWithSpacesAndHyphensAreNormalized() {
        RecordingConnection connection = enterAuthed("p6");
        connection.queueAnswer("man");
        connection.queueAnswer("high elf");
        connection.queueAnswer("fighter");

        characterCreate.onReceive(connection, "Hero");

        assertThat(connection.received).anyMatch(message -> message.equals(new CharacterCreated("Hero")));
    }

    @Test
    void fullHappyPathCreatesCharacterAndSendsCharacterCreatedAndStats() {
        RecordingConnection connection = enterAuthed("p7");
        Account account = authWorld.account(connection);
        connection.queueAnswer("woman");
        connection.queueAnswer("dwarf");
        connection.queueAnswer("wizard");

        characterCreate.onReceive(connection, "Hero");

        assertThat(connection.received).anyMatch(message -> message.equals(new CharacterCreated("Hero")));
        assertThat(connection.received).anyMatch(GamePlayerStats.class::isInstance);
        assertThat(gameWorld.isCharacterNameTaken(account.getId(), "Hero")).isTrue();
    }

    private RecordingConnection enterAuthed(String login) {
        raceService.warmRaceBonuses();
        classService.warmClassDefinitions();
        roomService.warmRooms();
        Account account = new Account(UUID.randomUUID(), login, "hashed-password", null);
        accountDao.insert(account);
        RecordingConnection connection = new RecordingConnection();
        authWorld.enterWorld(connection, account);
        connection.received.clear();
        return connection;
    }
}
