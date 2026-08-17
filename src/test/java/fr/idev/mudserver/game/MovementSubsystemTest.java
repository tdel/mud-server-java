package fr.idev.mudserver.game;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import fr.idev.mudserver.domain.actor.component.IdentityComponent;
import fr.idev.mudserver.domain.actor.component.MovementComponent;
import fr.idev.mudserver.domain.actor.component.PositionComponent;
import fr.idev.mudserver.domain.actor.instance.CharacterInstance;
import fr.idev.mudserver.domain.actor.system.MovementSystem;
import fr.idev.mudserver.domain.actor.system.NetworkSystem;
import fr.idev.mudserver.domain.map.HexCoordinate;
import fr.idev.mudserver.domain.map.HexDirection;
import fr.idev.mudserver.domain.world.RoomInstance;
import fr.idev.mudserver.domain.world.RoomTemplate;
import fr.idev.mudserver.domain.world.WorldInstance;

class MovementSubsystemTest {

    private final ExecutorService virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();
    private MovementSubsystem subsystem;

    @AfterEach
    void stopSubsystem() {
        if (subsystem != null) {
            subsystem.interrupt();
        }
    }

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

    private static void awaitUntil(BooleanSupplier condition, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(20);
        }
        assertThat(condition.getAsBoolean()).as("condition atteinte avant timeout").isTrue();
    }

    @Test
    void backgroundThreadMovesASingleCharacterOverTime() throws InterruptedException {
        ECS ecs = new ECS();
        MovementSystem movementSystem = new MovementSystem(null, new NetworkSystem(), virtualThreadExecutor, ecs);
        subsystem = new MovementSubsystem(movementSystem);
        subsystem.start();

        RoomInstance room = newRoom();
        HexCoordinate start = new HexCoordinate(2, 2);
        CharacterInstance character = newCharacter(room, start, 5); // cellSpeed = 1000ms
        ecs.register(character);
        character.attachComponent(new MovementComponent(HexDirection.E, 2, System.currentTimeMillis()));

        assertThat(subsystem.isAlive()).isTrue();

        HexCoordinate expectedFinal = start.neighbor(HexDirection.E).neighbor(HexDirection.E);
        awaitUntil(() -> character.findComponent(MovementComponent.class).isEmpty(), 5_000);

        assertThat(character.component(PositionComponent.class).hexCoordinate()).isEqualTo(expectedFinal);
    }

    @Test
    void backgroundThreadMovesTwoCharactersConcurrentlyAtTheirOwnSpeed() throws InterruptedException {
        ECS ecs = new ECS();
        MovementSystem movementSystem = new MovementSystem(null, new NetworkSystem(), virtualThreadExecutor, ecs);
        subsystem = new MovementSubsystem(movementSystem);
        subsystem.start();

        RoomInstance roomA = newRoom();
        HexCoordinate startA = new HexCoordinate(2, 2);
        CharacterInstance characterA = newCharacter(roomA, startA, 5); // cellSpeed = 1000ms
        ecs.register(characterA);

        RoomInstance roomB = newRoom();
        HexCoordinate startB = new HexCoordinate(10, 2);
        CharacterInstance characterB = newCharacter(roomB, startB, 10); // cellSpeed = 500ms
        ecs.register(characterB);

        long now = System.currentTimeMillis();
        characterA.attachComponent(new MovementComponent(HexDirection.E, 3, now));
        characterB.attachComponent(new MovementComponent(HexDirection.W, 3, now));

        HexCoordinate expectedFinalA = startA.neighbor(HexDirection.E).neighbor(HexDirection.E)
                .neighbor(HexDirection.E);
        HexCoordinate expectedFinalB = startB.neighbor(HexDirection.W).neighbor(HexDirection.W)
                .neighbor(HexDirection.W);

        // Le personnage B est deux fois plus rapide : il doit finir en premier.
        awaitUntil(() -> characterB.findComponent(MovementComponent.class).isEmpty(), 3_000);
        assertThat(characterB.component(PositionComponent.class).hexCoordinate()).isEqualTo(expectedFinalB);
        assertThat(characterA.findComponent(MovementComponent.class)).isPresent();

        awaitUntil(() -> characterA.findComponent(MovementComponent.class).isEmpty(), 5_000);
        assertThat(characterA.component(PositionComponent.class).hexCoordinate()).isEqualTo(expectedFinalA);

        // Les deux personnages ont bien avancé indépendamment, sans interférer l'un
        // avec l'autre.
        assertThat(roomA.occupantAt(expectedFinalA)).contains(characterA);
        assertThat(roomB.occupantAt(expectedFinalB)).contains(characterB);
    }
}
