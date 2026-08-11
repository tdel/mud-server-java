package fr.idev.mudserver.controller.ingame;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import fr.idev.mudserver.AbstractIntegrationTest;
import fr.idev.mudserver.controller.RecordingConnection;
import fr.idev.mudserver.domain.RoomInstance;
import fr.idev.mudserver.domain.actor.CharacterClass;
import fr.idev.mudserver.domain.actor.GameMonster;
import fr.idev.mudserver.domain.actor.GamePlayer;
import fr.idev.mudserver.domain.actor.Gender;
import fr.idev.mudserver.domain.actor.MonsterTemplate;
import fr.idev.mudserver.domain.actor.Race;
import fr.idev.mudserver.domain.actor.TestAttributes;
import fr.idev.mudserver.game.AuthWorld;
import fr.idev.mudserver.game.RoomService;
import fr.idev.mudserver.network.message.ingame.NoTargetSelected;
import fr.idev.mudserver.network.message.ingame.TargetNotFound;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ne re-teste pas la résolution de combat elle-même (déjà couverte par
 * {@code CombatEngineTest}) : seulement le branchement de résolution de cible
 * propre à {@link Attack} (réutilisation de la cible sélectionnée, cible
 * caduque, résolution par argument). Un vrai {@code CombatEngine} autowired est
 * utilisé (pas de mock) — la délégation se prouve par l'effet de bord
 * observable ({@code isInCombat()}), même approche que
 * {@code CombatEngineTest}.
 */
@Transactional
class AttackTest extends AbstractIntegrationTest {

    @Autowired
    private Attack attack;

    @Autowired
    private AuthWorld authWorld;

    @Autowired
    private RoomService roomService;

    @Test
    void noArgumentAndNoExistingTargetSendsNoTargetSelected() {
        RecordingConnection connection = enterGame();

        attack.onReceive(connection, "");

        assertThat(connection.received).containsExactly(new NoTargetSelected());
    }

    @Test
    void noArgumentWithExistingTargetReusesTargetAndDelegatesToCombatEngine() {
        RecordingConnection connection = enterGame();
        GamePlayer character = connection.character();
        GameMonster monster = monster(character.getCurrentRoom());
        character.setTarget(monster);

        attack.onReceive(connection, "");

        assertThat(connection.received).noneMatch(NoTargetSelected.class::isInstance)
                .noneMatch(TargetNotFound.class::isInstance);
        assertThat(character.isInCombat()).as("delegated to CombatEngine.attack").isTrue();
    }

    @Test
    void noArgumentWithStaleTargetThatLeftTheRoomSendsTargetNotFoundAndClearsTheTarget() {
        RecordingConnection connection = enterGame();
        GamePlayer character = connection.character();
        RoomInstance elsewhere = new RoomInstance(UUID.randomUUID(), "Ailleurs", "...", null);
        GameMonster monster = monster(elsewhere);
        character.setTarget(monster);

        attack.onReceive(connection, "");

        assertThat(connection.received).containsExactly(new TargetNotFound(monster.getName()));
        assertThat(character.getTarget()).isNull();
    }

    @Test
    void withArgumentMatchingMonsterInRoomSetsTargetAndDelegatesToCombatEngine() {
        RecordingConnection connection = enterGame();
        GamePlayer character = connection.character();
        GameMonster monster = monster(character.getCurrentRoom());

        attack.onReceive(connection, monster.getName());

        assertThat(character.getTarget()).isEqualTo(monster);
        assertThat(character.isInCombat()).as("delegated to CombatEngine.attack").isTrue();
    }

    @Test
    void withArgumentNotMatchingAnyMonsterSendsTargetNotFound() {
        RecordingConnection connection = enterGame();

        attack.onReceive(connection, "ghost");

        assertThat(connection.received).containsExactly(new TargetNotFound("ghost"));
    }

    private RecordingConnection enterGame() {
        roomService.warmRooms();
        RoomInstance startingRoom = roomService.startingRoom().orElseThrow();
        RecordingConnection connection = new RecordingConnection();
        GamePlayer character = new GamePlayer(UUID.randomUUID(), UUID.randomUUID(), "Attaquant", startingRoom.getId(),
                Gender.MAN, Race.HUMAN, CharacterClass.FIGHTER, 1, 1000, 1000,
                TestAttributes.of(10, 10, 10, 10, 10, 10), 0, 0);
        authWorld.enterGameWorld(connection, character);
        connection.received.clear();
        return connection;
    }

    private GameMonster monster(RoomInstance room) {
        MonsterTemplate template = new MonsterTemplate(UUID.randomUUID(), "Mannequin " + UUID.randomUUID(),
                "Un mannequin d'entraînement", 1000, TestAttributes.of(10, 10, 10, 10, 10, 10), 10, 0, "1d6", 0,
                List.of(), 0);
        GameMonster monster = new GameMonster(UUID.randomUUID(), template.getName(), template.getId(), room.getId(),
                template.getAttributes(), 1000);
        monster.attachTemplate(template);
        monster.setCurrentRoom(room);
        room.addMonster(monster);
        return monster;
    }
}
