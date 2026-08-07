package fr.idev.mudserver.game.actor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import fr.idev.mudserver.AbstractIntegrationTest;
import fr.idev.mudserver.domain.Account;
import fr.idev.mudserver.domain.Room;
import fr.idev.mudserver.domain.actor.CharacterClass;
import fr.idev.mudserver.domain.actor.Gender;
import fr.idev.mudserver.domain.actor.GamePlayer;
import fr.idev.mudserver.domain.actor.Race;
import fr.idev.mudserver.domain.actor.TestAttributes;
import fr.idev.mudserver.domain.actor.TestProficiencies;
import fr.idev.mudserver.network.Connection;
import fr.idev.mudserver.network.ConnectionState;
import fr.idev.mudserver.network.OutputMessage;
import fr.idev.mudserver.domain.Item;
import fr.idev.mudserver.game.ItemService;
import fr.idev.mudserver.network.message.ingame.GoldLooted;
import fr.idev.mudserver.network.message.ingame.GoldSpent;
import fr.idev.mudserver.network.message.ingame.PlayerLeveledUp;
import fr.idev.mudserver.network.message.ingame.XpGained;
import fr.idev.mudserver.persistence.AccountDao;
import fr.idev.mudserver.persistence.CharacterDao;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code CharacterService.onCharacterGainedXp} n'est jamais appelé directement
 * ici : ces tests passent par {@link GamePlayer#gainXp} pour vérifier le
 * mécanisme de bout en bout, comme {@code ItemServiceTest} le fait pour
 * {@code equipItem}/{@code ItemService}.
 */
@Transactional
class CharacterServiceTest extends AbstractIntegrationTest {

    @Autowired
    private AccountDao accountDao;

    @Autowired
    private CharacterDao characterDao;

    @Autowired
    private ClassService classService;

    @Autowired
    private LevelService levelService;

    @Autowired
    private ItemService itemService;

    private GamePlayer fighter(int level, int xp) {
        Account account = new Account(UUID.randomUUID(), "hero-" + UUID.randomUUID(), "hashed-password", null);
        accountDao.insert(account);
        // FIGHTER, CON 10 (modificateur nul) : hpGain par niveau = hitDie/2+1+0 = 6.
        GamePlayer character = new GamePlayer(UUID.randomUUID(), account.getId(), "Héros", UUID.randomUUID(),
                Gender.MAN, Race.HUMAN, CharacterClass.FIGHTER, TestProficiencies.savingThrows(CharacterClass.FIGHTER),
                TestProficiencies.skills(CharacterClass.FIGHTER), level, 10, 10,
                TestAttributes.of(10, 10, 10, 10, 10, 10), xp, 0);
        characterDao.insert(character);
        // CharacterService diffuse toujours à character.getCurrentRoom() en cas de
        // montée de niveau : il faut donc une room, même quand le test ne
        // s'intéresse pas à la diffusion elle-même.
        new Room(UUID.randomUUID(), "Place du village", "...", null).join(character);
        return character;
    }

    @Test
    void crossingOneThresholdLevelsUpAndGrantsHitPointsFromTheClassHitDie() {
        classService.warmClassDefinitions();
        levelService.warmXpThresholds();
        GamePlayer character = fighter(1, 0);

        character.gainXp(300);

        assertThat(character.getLevel()).isEqualTo(2);
        assertThat(character.getMaxHealth()).isEqualTo(16);
        assertThat(character.getCurrentHealth()).isEqualTo(16);
        assertThat(character.getXp()).isEqualTo(300);
        assertThat(characterDao.findById(character.getId())).contains(character);
    }

    @Test
    void crossingTwoThresholdsInOneGainLevelsUpTwiceApplyingHpGainEachTime() {
        classService.warmClassDefinitions();
        levelService.warmXpThresholds();
        GamePlayer character = fighter(1, 0);

        character.gainXp(950);

        assertThat(character.getLevel()).isEqualTo(3);
        assertThat(character.getMaxHealth()).isEqualTo(22);
        assertThat(character.getCurrentHealth()).isEqualTo(22);
        assertThat(character.getXp()).isEqualTo(950);
    }

    @Test
    void gainingXpWithoutCrossingAThresholdLeavesLevelUnchangedButPersistsXp() {
        classService.warmClassDefinitions();
        levelService.warmXpThresholds();
        GamePlayer character = fighter(1, 0);

        character.gainXp(50);

        assertThat(character.getLevel()).isEqualTo(1);
        assertThat(character.getMaxHealth()).isEqualTo(10);
        assertThat(character.getXp()).isEqualTo(50);
        assertThat(characterDao.findById(character.getId())).contains(character);
    }

    @Test
    void levelTwentyIsACapEvenWithOverwhelmingXp() {
        classService.warmClassDefinitions();
        levelService.warmXpThresholds();
        GamePlayer character = fighter(20, 355000);

        character.gainXp(1_000_000);

        assertThat(character.getLevel()).isEqualTo(20);
        assertThat(character.getXp()).isEqualTo(1_355_000);
    }

    @Test
    void levelingUpBroadcastsOnlyToPlayersInTheSameRoom() {
        classService.warmClassDefinitions();
        levelService.warmXpThresholds();

        GamePlayer leveler = fighter(1, 0);
        GamePlayer bystander = fighter(1, 0);
        GamePlayer stranger = fighter(1, 0);

        RecordingConnection levelerConnection = new RecordingConnection();
        RecordingConnection bystanderConnection = new RecordingConnection();
        RecordingConnection strangerConnection = new RecordingConnection();
        leveler.setConnection(levelerConnection);
        bystander.setConnection(bystanderConnection);
        stranger.setConnection(strangerConnection);

        Room room = new Room(UUID.randomUUID(), "Place du village", "...", null);
        Room otherRoom = new Room(UUID.randomUUID(), "Clairière", "...", null);
        room.join(leveler);
        room.join(bystander);
        otherRoom.join(stranger);

        levelerConnection.received.clear();
        bystanderConnection.received.clear();
        strangerConnection.received.clear();

        leveler.gainXp(300);

        assertThat(levelerConnection.received).containsSubsequence(new XpGained(300),
                new PlayerLeveledUp(leveler.getName(), 2));
        assertThat(bystanderConnection.received)
                .anySatisfy(message -> assertThat(message).isEqualTo(new PlayerLeveledUp(leveler.getName(), 2)));
        assertThat(strangerConnection.received).noneMatch(PlayerLeveledUp.class::isInstance);
    }

    @Test
    void receivingGoldPersistsItAndNotifiesTheCharacterOnly() {
        GamePlayer character = fighter(1, 0);
        RecordingConnection connection = new RecordingConnection();
        character.setConnection(connection);

        character.receiveGold(25);

        assertThat(character.getInventory().getGold()).isEqualTo(25);
        assertThat(connection.received).containsExactly(new GoldLooted(25));
        assertThat(characterDao.findById(character.getId())).contains(character);
    }

    @Test
    void spendingGoldPersistsItAndNotifiesTheCharacterOnly() {
        GamePlayer character = fighter(1, 0);
        character.receiveGold(100);
        RecordingConnection connection = new RecordingConnection();
        character.setConnection(connection);

        itemService.warmItemTemplates();
        UUID potionTemplateId = UUID.fromString("019fa0a5-80bf-7e84-87bf-5cf699c00315");
        Item item = new Item(UUID.randomUUID(), potionTemplateId, null, character.getId(), null);
        boolean bought = character.buyItem(item, 50);

        assertThat(bought).isTrue();
        assertThat(character.getInventory().getGold()).isEqualTo(50);
        assertThat(connection.received).contains(new GoldSpent(50));
        assertThat(characterDao.findById(character.getId()).map(c -> c.getInventory().getGold())).contains(50);
    }

    @Test
    void gainingXpWithoutCrossingAThresholdStillSendsXpGainedButNoLevelUpMessage() {
        classService.warmClassDefinitions();
        levelService.warmXpThresholds();
        GamePlayer character = fighter(1, 0);
        RecordingConnection connection = new RecordingConnection();
        character.setConnection(connection);

        character.gainXp(50);

        assertThat(connection.received).containsExactly(new XpGained(50));
    }

    private static final class RecordingConnection implements Connection {

        private final List<OutputMessage> received = new ArrayList<>();

        @Override
        public void requestBlocking(OutputMessage message, Consumer<String> handler) {
            // non utilisé par ces tests
        }

        @Override
        public ConnectionState state() {
            return ConnectionState.INGAME;
        }

        @Override
        public void setState(ConnectionState state) {
            // non utilisé par ces tests
        }

        @Override
        public void send(OutputMessage message) {
            received.add(message);
        }

        @Override
        public void close() {
            // non utilisé par ces tests
        }
    }
}
