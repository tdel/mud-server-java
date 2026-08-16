package fr.idev.mudserver.domain.actor.system;

import java.util.ArrayList;
import java.util.List;

import fr.idev.mudserver.domain.actor.AbstractCharacter;
import fr.idev.mudserver.domain.actor.component.MovementComponent;
import fr.idev.mudserver.domain.actor.component.MovementComponent.ActiveMovement;
import fr.idev.mudserver.domain.actor.event.CharacterStartedMoving;
import fr.idev.mudserver.domain.actor.event.CharacterStoppedMoving;
import fr.idev.mudserver.domain.actor.event.DomainEventPublisher;
import fr.idev.mudserver.domain.map.HexCoordinate;
import fr.idev.mudserver.domain.map.HexDirection;
import fr.idev.mudserver.domain.world.RoomInstance;

public final class MovementSystem {

    public static final int REFERENCE_SPEED = 5;
    public static final long REFERENCE_TIME_MS = 1000L;

    private MovementSystem() {
    }

    public static CellStepOutcome moveOneCell(AbstractCharacter character, HexDirection direction) {
        RoomInstance room = character.getCurrentRoom();
        HexCoordinate current = character.getPosition();
        HexCoordinate next = current.neighbor(direction);

        if (!room.isInBounds(next)) {
            return new CellStepOutcome(false, true, false);
        }
        if (!room.tryClaimCell(next, character)) {
            return new CellStepOutcome(false, false, true);
        }

        room.releaseCell(current, character);
        character.setPosition(next);

        return new CellStepOutcome(true, false, false);
    }

    public static List<HexCoordinate> remainingPath(AbstractCharacter character) {
        ActiveMovement movement = character.component(MovementComponent.class).activeMovement();
        if (movement == null) {
            return List.of();
        }
        List<HexCoordinate> path = new ArrayList<>(movement.cellsRemaining());
        HexCoordinate cursor = character.getPosition();
        for (int i = 0; i < movement.cellsRemaining(); i++) {
            cursor = cursor.neighbor(movement.direction());
            path.add(cursor);
        }
        return path;
    }

    public static void startMovement(AbstractCharacter character, HexDirection direction, int cellsRequested) {
        character.updateComponent(MovementComponent.class, current -> new MovementComponent(current.speed(),
                new ActiveMovement(direction, cellsRequested, System.currentTimeMillis())));
        DomainEventPublisher.publish(new CharacterStartedMoving(character));
    }

    public static void stopMovement(AbstractCharacter character) {
        if (character.component(MovementComponent.class).activeMovement() == null) {
            return;
        }
        character.updateComponent(MovementComponent.class, current -> new MovementComponent(current.speed(), null));
        DomainEventPublisher.publish(new CharacterStoppedMoving(character));
    }

    public static MovementStepOutcome updatePosition(AbstractCharacter character, long now) {
        ActiveMovement movement = character.component(MovementComponent.class).activeMovement();
        if (movement == null || now - movement.lastStepAt() < getMillisPerCell(character)) {
            return MovementStepOutcome.NO_MOVEMENT;
        }

        CellStepOutcome step = moveOneCell(character, movement.direction());
        if (!step.moved()) {
            character.updateComponent(MovementComponent.class, current -> new MovementComponent(current.speed(), null));
            return step.blockedByOccupant()
                    ? MovementStepOutcome.BLOCKED_BY_OCCUPANT
                    : MovementStepOutcome.BLOCKED_BY_BOUNDS;
        }

        int remaining = movement.cellsRemaining() - 1;
        if (remaining <= 0) {
            character.updateComponent(MovementComponent.class, current -> new MovementComponent(current.speed(), null));
            return MovementStepOutcome.FINISHED;
        }
        ActiveMovement updatedMovement = movement.withRemaining(remaining, now);
        character.updateComponent(MovementComponent.class,
                current -> new MovementComponent(current.speed(), updatedMovement));
        return MovementStepOutcome.STEPPED;
    }

    private static long getMillisPerCell(AbstractCharacter character) {
        return REFERENCE_TIME_MS * REFERENCE_SPEED / Math.max(1, character.component(MovementComponent.class).speed());
    }

    public record CellStepOutcome(boolean moved, boolean blockedByBounds, boolean blockedByOccupant) {
    }

    public enum MovementStepOutcome {
        NO_MOVEMENT, STEPPED, FINISHED, BLOCKED_BY_BOUNDS, BLOCKED_BY_OCCUPANT
    }
}
