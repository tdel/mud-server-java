package fr.idev.mudserver.controller;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import fr.idev.mudserver.AbstractIntegrationTest;
import fr.idev.mudserver.domain.RoomInstance;
import fr.idev.mudserver.domain.WorldInstance;
import fr.idev.mudserver.domain.actor.CharacterClass;
import fr.idev.mudserver.domain.actor.CombatEncounter;
import fr.idev.mudserver.domain.actor.GameMonster;
import fr.idev.mudserver.domain.actor.GamePlayer;
import fr.idev.mudserver.domain.actor.Gender;
import fr.idev.mudserver.domain.actor.Race;
import fr.idev.mudserver.domain.actor.TestAttributes;
import fr.idev.mudserver.game.RoomService;
import fr.idev.mudserver.game.WorldInstanceService;
import fr.idev.mudserver.network.ConnectionState;
import fr.idev.mudserver.network.message.ActionNotFound;
import fr.idev.mudserver.network.message.ingame.CombatActionRequired;
import fr.idev.mudserver.network.message.ingame.RoomDescription;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contexte Spring requis : exerce le vrai
 * {@link WorldInstanceService}/{@link ControllerRegistry}, seul moyen fiable de
 * reproduire l'état "connexion en jeu" que le verrouillage de combat de
 * {@link ControllerDispatcher} vérifie. Aucune couverture n'existait au niveau
 * {@code controller/**} avant ce test — la logique de verrouillage est neuve et
 * facile à inverser par erreur (whitelist vs blacklist), d'où ce test dédié
 * malgré l'absence de précédent dans ce package.
 */
class ControllerDispatcherTest extends AbstractIntegrationTest {

    @Autowired
    private ControllerDispatcher dispatcher;

    @Autowired
    private WorldInstanceService worldInstanceService;

    @Autowired
    private RoomService roomService;

    @Test
    void combatWhitelistedVerbPassesThroughWhileInCombat() {
        RecordingConnection connection = enterGameInCombat();

        dispatcher.dispatch(connection, "look", "");

        assertThat(connection.received).anyMatch(RoomDescription.class::isInstance);
        assertThat(connection.received).noneMatch(CombatActionRequired.class::isInstance);
    }

    @Test
    void nonWhitelistedVerbIsRejectedWhileInCombat() {
        RecordingConnection connection = enterGameInCombat();

        dispatcher.dispatch(connection, "go", "north");

        assertThat(connection.received).hasSize(1);
        assertThat(connection.received.get(0)).isInstanceOf(CombatActionRequired.class);
    }

    @Test
    void verbResolutionIsUnaffectedOutsideIngameState() {
        RecordingConnection connection = new RecordingConnection();
        connection.setState(ConnectionState.CONNECTED);

        dispatcher.dispatch(connection, "definitely-not-a-real-verb", "");

        assertThat(connection.received).hasSize(1);
        assertThat(connection.received.get(0)).isInstanceOf(ActionNotFound.class);
    }

    private RecordingConnection enterGameInCombat() {
        roomService.warmRooms();
        RoomInstance startingRoom = roomService.startingRoom().orElseThrow();
        RecordingConnection connection = new RecordingConnection();
        GamePlayer character = new GamePlayer(UUID.randomUUID(), UUID.randomUUID(), "Combattant", startingRoom.getId(),
                Gender.MAN, Race.HUMAN, CharacterClass.FIGHTER, 1, 10, 10, TestAttributes.of(10, 10, 10, 10, 10, 10), 0,
                0);
        worldInstanceService.enterCharSelect(connection,
                worldInstanceService.getOrMaterialize(WorldInstance.DEFAULT_ID));
        worldInstanceService.enterGame(connection, character);

        GameMonster monster = new GameMonster(UUID.randomUUID(), "Mannequin", UUID.randomUUID(), startingRoom.getId(),
                TestAttributes.of(10, 10, 10, 10, 10, 10), 1000);
        monster.setCurrentRoom(startingRoom);
        character.setEncounter(new CombatEncounter(startingRoom));
        monster.setEncounter(character.getEncounter());

        connection.received.clear();
        return connection;
    }
}
