package fr.idev.mudserver.domain.actor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import fr.idev.mudserver.AbstractIntegrationTest;
import fr.idev.mudserver.domain.Account;
import fr.idev.mudserver.domain.Room;
import fr.idev.mudserver.game.RoomService;
import fr.idev.mudserver.game.actor.ClassService;
import fr.idev.mudserver.game.actor.RaceService;
import fr.idev.mudserver.network.Connection;
import fr.idev.mudserver.network.ConnectionState;
import fr.idev.mudserver.network.OutputMessage;
import fr.idev.mudserver.network.message.ingame.GamePlayerDefeated;
import fr.idev.mudserver.network.message.ingame.PlayerRespawned;
import fr.idev.mudserver.persistence.AccountDao;
import fr.idev.mudserver.persistence.CharacterDao;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contexte Spring requis (pas {@code @Transactional}), sur le modèle de
 * {@code GameMonsterTest} : sur le coup fatal, {@link GamePlayer#takeDamage}
 * publie {@code GamePlayerDied} via le holder statique
 * {@code DomainEventPublisher}, dont les listeners
 * ({@code RoomService}/{@code CharacterService}) écrivent en DB — ces écritures
 * ne doivent pas partager la connexion/transaction du thread de test.
 */
class GamePlayerDeathTest extends AbstractIntegrationTest {

    @Autowired
    private RoomService roomService;

    @Autowired
    private AccountDao accountDao;

    @Autowired
    private CharacterDao characterDao;

    @Autowired
    private ClassService classService;

    @Autowired
    private RaceService raceService;

    @Test
    void takeDamageReducesHealthWithoutGoingBelowZeroAndReportsTheKillingBlow() {
        GamePlayer character = seedCharacter(deathRoom());
        GameMonster attacker = monster();

        assertThat(character.takeDamage(3, attacker)).isFalse();
        assertThat(character.getCurrentHealth()).isEqualTo(7);

        // Le coup fatal clampe les PV à 0 avant de publier GamePlayerDied ; dans ce
        // contexte
        // Spring complet, le listener de réapparition
        // (CharacterService#onGamePlayerDied)
        // restaure aussitôt les PV au max en réaction à ce même événement, donc ce
        // clamp à 0
        // n'est observable de l'extérieur qu'à travers la valeur de retour (true = coup
        // fatal) — voir
        // theKillingBlowRestoresFullHealthTeleportsToTheStartingRoomAndPersists
        // pour la vérification bout-en-bout de la chaîne de réapparition.
        assertThat(character.takeDamage(100, attacker)).isTrue();
        assertThat(character.getCurrentHealth()).isEqualTo(character.getMaxHealth());
    }

    @Test
    void takeDamageOnAnAlreadyDefeatedCharacterIsANoOp() {
        GamePlayer character = seedCharacter(deathRoom());
        GameMonster attacker = monster();
        // Simule un personnage déjà à terre sans passer par takeDamage/l'événement de
        // mort
        // (qui déclencherait sinon la réapparition automatique et fausserait ce test —
        // voir
        // le commentaire du test précédent) : seule façon d'observer depuis l'extérieur
        // la
        // garde "déjà mort" de takeDamage dans ce contexte Spring complet.
        character.setCurrentHealth(0);

        assertThat(character.takeDamage(5, attacker)).isFalse();
        assertThat(character.getCurrentHealth()).isZero();
    }

    @Test
    void theKillingBlowRestoresFullHealthTeleportsToTheStartingRoomAndPersists() {
        roomService.warmRooms();
        Room startingRoom = roomService.startingRoom().orElseThrow();
        GamePlayer character = seedCharacter(deathRoom());
        GameMonster attacker = monster();

        character.takeDamage(100, attacker);

        assertThat(character.getCurrentHealth()).isEqualTo(character.getMaxHealth());
        assertThat(character.getCurrentRoom()).isEqualTo(startingRoom);

        GamePlayer persisted = characterDao.findById(character.getId()).orElseThrow();
        assertThat(persisted.getCurrentHealth()).isEqualTo(character.getMaxHealth());
        assertThat(persisted.getCurrentRoomId()).isEqualTo(startingRoom.getId());
    }

    @Test
    void theKillingBlowBroadcastsToTheDeathRoomExcludingTheVictimAndTheVictimReceivesItsOwnRespawnMessage() {
        roomService.warmRooms();
        Room deathRoom = deathRoom();
        GamePlayer victim = seedCharacter(deathRoom);
        GamePlayer bystander = seedCharacter(deathRoom);
        RecordingConnection victimConnection = new RecordingConnection();
        RecordingConnection bystanderConnection = new RecordingConnection();
        victim.setConnection(victimConnection);
        bystander.setConnection(bystanderConnection);
        GameMonster attacker = monster();

        victim.takeDamage(100, attacker);

        assertThat(bystanderConnection.received).anyMatch(GamePlayerDefeated.class::isInstance);
        assertThat(victimConnection.received).noneMatch(GamePlayerDefeated.class::isInstance);
        assertThat(victimConnection.received).anyMatch(PlayerRespawned.class::isInstance);
    }

    private Room deathRoom() {
        return new Room(UUID.randomUUID(), "Fosse aux ours", "...", null);
    }

    private GamePlayer seedCharacter(Room room) {
        classService.warmClassDefinitions();
        raceService.warmRaceBonuses();
        Account account = new Account(UUID.randomUUID(), "death-test-" + UUID.randomUUID(), "hashed-password", null);
        accountDao.insert(account);
        GamePlayer character = new GamePlayer(UUID.randomUUID(), account.getId(), account.getLogin(), room.getId(),
                Gender.MAN, Race.HUMAN, CharacterClass.FIGHTER, TestProficiencies.savingThrows(CharacterClass.FIGHTER),
                TestProficiencies.skills(CharacterClass.FIGHTER), 1, 10, 10, TestAttributes.of(10, 10, 10, 10, 10, 10),
                0, 0);
        characterDao.insert(character);
        room.join(character);
        return character;
    }

    private GameMonster monster() {
        return new GameMonster(UUID.randomUUID(), "Attaquant", UUID.randomUUID(), UUID.randomUUID(),
                TestAttributes.of(10, 10, 10, 10, 10, 10), 10);
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
