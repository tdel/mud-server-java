package fr.idev.mudserver.domain.actor.system;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.Test;

import fr.idev.mudserver.domain.actor.component.IdentityComponent;
import fr.idev.mudserver.domain.actor.component.MovementComponent;
import fr.idev.mudserver.domain.actor.component.PositionComponent;
import fr.idev.mudserver.domain.actor.instance.CharacterInstance;
import fr.idev.mudserver.domain.map.HexCoordinate;
import fr.idev.mudserver.domain.map.HexDirection;
import fr.idev.mudserver.domain.world.RoomInstance;
import fr.idev.mudserver.domain.world.RoomTemplate;
import fr.idev.mudserver.domain.world.WorldInstance;
import fr.idev.mudserver.game.ECS;

class MovementSystemTest {

    private final ExecutorService virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();

    private RoomInstance newRoom() {
        RoomTemplate template = new RoomTemplate(UUID.randomUUID(), "Salle de test", "desc", false, 20, 20,
                new HexCoordinate(0, 0), List.of());
        WorldInstance world = new WorldInstance(UUID.randomUUID(), UUID.randomUUID(), Instant.now(), null, Set.of());
        return new RoomInstance(UUID.randomUUID(), template, world);
    }

    private CharacterInstance newCharacter(RoomInstance room, HexCoordinate start, int speed) {
        CharacterInstance character = new CharacterInstance(UUID.randomUUID());
        character.attachComponent(new IdentityComponent("Hero-" + character.getId(), speed));
        room.tryClaimCell(start, character);
        character.attachComponent(new PositionComponent(room, start));
        return character;
    }

    @Test
    void updateAdvancesOneCellPerCharacterSpeedAndStopsWhenPathIsFinished() {
        ECS ecs = new ECS();
        MovementSystem movementSystem = new MovementSystem(null, new NetworkSystem(), virtualThreadExecutor, ecs);

        RoomInstance room = newRoom();
        HexCoordinate start = new HexCoordinate(2, 2);
        CharacterInstance character = newCharacter(room, start, 5); // cellSpeed = 1000ms
        ecs.register(character);

        long t0 = System.currentTimeMillis();
        character.attachComponent(new MovementComponent(HexDirection.E, 3, t0));

        // Pas assez de temps écoulé : aucun déplacement
        movementSystem.update(t0 + 500);
        assertThat(character.component(PositionComponent.class).hexCoordinate()).isEqualTo(start);

        // Une "cellSpeed" (1000ms) s'est écoulée : une case franchie
        movementSystem.update(t0 + 1000);
        HexCoordinate afterFirstStep = start.neighbor(HexDirection.E);
        assertThat(character.component(PositionComponent.class).hexCoordinate()).isEqualTo(afterFirstStep);
        assertThat(character.component(MovementComponent.class).cellsRemaining()).isEqualTo(2);

        // Pas assez de temps depuis le dernier pas : toujours immobile
        movementSystem.update(t0 + 1999);
        assertThat(character.component(PositionComponent.class).hexCoordinate()).isEqualTo(afterFirstStep);

        movementSystem.update(t0 + 2000);
        HexCoordinate afterSecondStep = afterFirstStep.neighbor(HexDirection.E);
        assertThat(character.component(PositionComponent.class).hexCoordinate()).isEqualTo(afterSecondStep);

        movementSystem.update(t0 + 3000);
        HexCoordinate afterThirdStep = afterSecondStep.neighbor(HexDirection.E);
        assertThat(character.component(PositionComponent.class).hexCoordinate()).isEqualTo(afterThirdStep);
        // Trajet terminé : le MovementComponent est détaché
        assertThat(character.findComponent(MovementComponent.class)).isEmpty();

        // Un update supplémentaire ne fait plus rien avancer
        movementSystem.update(t0 + 4000);
        assertThat(character.component(PositionComponent.class).hexCoordinate()).isEqualTo(afterThirdStep);
    }

    @Test
    void updateStopsAndDetachesMovementComponentWhenNextCellIsOutOfBounds() {
        ECS ecs = new ECS();
        MovementSystem movementSystem = new MovementSystem(null, new NetworkSystem(), virtualThreadExecutor, ecs);

        RoomInstance room = newRoom();
        HexCoordinate start = new HexCoordinate(0, 2);
        CharacterInstance character = newCharacter(room, start, 5);
        ecs.register(character);

        long t0 = System.currentTimeMillis();
        character.attachComponent(new MovementComponent(HexDirection.W, 1, t0));

        movementSystem.update(t0 + 1000);

        assertThat(character.component(PositionComponent.class).hexCoordinate()).isEqualTo(start);
        assertThat(character.findComponent(MovementComponent.class)).isEmpty();
    }
}
