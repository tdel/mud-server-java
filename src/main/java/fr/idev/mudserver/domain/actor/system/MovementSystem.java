package fr.idev.mudserver.domain.actor.system;

import java.util.ArrayList;
import java.util.List;

import fr.idev.mudserver.domain.actor.component.PositionComponent;
import org.springframework.stereotype.Service;

import fr.idev.mudserver.domain.actor.AbstractCharacter;
import fr.idev.mudserver.domain.actor.component.MovementComponent;
import fr.idev.mudserver.domain.actor.component.MovementComponent.ActiveMovement;
import fr.idev.mudserver.domain.actor.event.CharacterStartedMoving;
import fr.idev.mudserver.domain.actor.event.CharacterStoppedMoving;
import fr.idev.mudserver.domain.actor.event.DomainEventPublisher;
import fr.idev.mudserver.domain.map.HexCoordinate;
import fr.idev.mudserver.domain.map.HexDirection;
import fr.idev.mudserver.domain.world.RoomInstance;

@Service
public class MovementSystem {

    public static final int REFERENCE_SPEED = 5;
    public static final long REFERENCE_TIME_MS = 1000L;

    public CellStepOutcome moveOneCell(AbstractCharacter character, HexDirection direction) {
        PositionComponent position = character.component(PositionComponent.class);
        RoomInstance room = position.currentRoom();
        HexCoordinate currentCoord = position.hexCoordinate();
        HexCoordinate nextCoord = currentCoord.neighbor(direction);

        if (!room.isInBounds(nextCoord)) {
            return new CellStepOutcome(false, true, false);
        }
        if (!room.tryClaimCell(nextCoord, character)) {
            return new CellStepOutcome(false, false, true);
        }

        room.releaseCell(currentCoord, character);
        character.updateComponent(PositionComponent.class, current -> new PositionComponent(room, nextCoord));

        return new CellStepOutcome(true, false, false);
    }

    public List<HexCoordinate> remainingPath(AbstractCharacter character) {
        ActiveMovement movement = character.component(MovementComponent.class).activeMovement();
        if (movement == null) {
            return List.of();
        }
        List<HexCoordinate> path = new ArrayList<>(movement.cellsRemaining());
        HexCoordinate cursor = character.component(PositionComponent.class).hexCoordinate();
        for (int i = 0; i < movement.cellsRemaining(); i++) {
            cursor = cursor.neighbor(movement.direction());
            path.add(cursor);
        }
        return path;
    }

    public void startMovement(AbstractCharacter character, HexDirection direction, int cellsRequested) {
        character.updateComponent(MovementComponent.class, current -> new MovementComponent(current.speed(),
                new ActiveMovement(direction, cellsRequested, System.currentTimeMillis())));
        DomainEventPublisher.publish(new CharacterStartedMoving(character));
    }

    public void stopMovement(AbstractCharacter character) {
        if (character.component(MovementComponent.class).activeMovement() == null) {
            return;
        }
        character.updateComponent(MovementComponent.class, current -> new MovementComponent(current.speed(), null));
        DomainEventPublisher.publish(new CharacterStoppedMoving(character));
    }

    public MovementStepOutcome updatePosition(AbstractCharacter character, long now) {
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

    private long getMillisPerCell(AbstractCharacter character) {
        return REFERENCE_TIME_MS * REFERENCE_SPEED / Math.max(1, character.component(MovementComponent.class).speed());
    }

    public record CellStepOutcome(boolean moved, boolean blockedByBounds, boolean blockedByOccupant) {
    }

    public enum MovementStepOutcome {
        NO_MOVEMENT, STEPPED, FINISHED, BLOCKED_BY_BOUNDS, BLOCKED_BY_OCCUPANT
    }
}
