package fr.idev.mudserver.domain.actor.system;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import fr.idev.mudserver.domain.actor.component.PositionComponent;
import org.springframework.stereotype.Service;

import fr.idev.mudserver.domain.actor.AbstractCharacter;
import fr.idev.mudserver.domain.actor.component.IdentityComponent;
import fr.idev.mudserver.domain.actor.component.MovementComponent;
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
        Optional<MovementComponent> movement = character.findComponent(MovementComponent.class);
        if (movement.isEmpty()) {
            return List.of();
        }
        int cellsRemaining = movement.get().cellsRemaining();
        HexDirection direction = movement.get().direction();
        List<HexCoordinate> path = new ArrayList<>(cellsRemaining);
        HexCoordinate cursor = character.component(PositionComponent.class).hexCoordinate();
        for (int i = 0; i < cellsRemaining; i++) {
            cursor = cursor.neighbor(direction);
            path.add(cursor);
        }
        return path;
    }

    public void startMovement(AbstractCharacter character, HexDirection direction, int cellsRequested) {
        character.updateComponent(MovementComponent.class,
                current -> new MovementComponent(direction, cellsRequested, System.currentTimeMillis()));
        DomainEventPublisher.publish(new CharacterStartedMoving(character));
    }

    public void stopMovement(AbstractCharacter character) {
        if (character.findComponent(MovementComponent.class).isEmpty()) {
            return;
        }
        character.detachComponent(MovementComponent.class);
        DomainEventPublisher.publish(new CharacterStoppedMoving(character));
    }

    public MovementStepOutcome updatePosition(AbstractCharacter character, long now) {
        Optional<MovementComponent> movementComponent = character.findComponent(MovementComponent.class);
        if (movementComponent.isEmpty()) {
            return MovementStepOutcome.NO_MOVEMENT;
        }
        MovementComponent movement = movementComponent.get();
        if (now - movement.lastStepAt() < getMillisPerCell(character)) {
            return MovementStepOutcome.NO_MOVEMENT;
        }

        CellStepOutcome step = moveOneCell(character, movement.direction());
        if (!step.moved()) {
            character.detachComponent(MovementComponent.class);
            return step.blockedByOccupant()
                    ? MovementStepOutcome.BLOCKED_BY_OCCUPANT
                    : MovementStepOutcome.BLOCKED_BY_BOUNDS;
        }

        int remaining = movement.cellsRemaining() - 1;
        if (remaining <= 0) {
            character.detachComponent(MovementComponent.class);
            return MovementStepOutcome.FINISHED;
        }
        character.updateComponent(MovementComponent.class, current -> current.withRemaining(remaining, now));
        return MovementStepOutcome.STEPPED;
    }

    private long getMillisPerCell(AbstractCharacter character) {
        return REFERENCE_TIME_MS * REFERENCE_SPEED / Math.max(1, character.component(IdentityComponent.class).speed());
    }

    public record CellStepOutcome(boolean moved, boolean blockedByBounds, boolean blockedByOccupant) {
    }

    public enum MovementStepOutcome {
        NO_MOVEMENT, STEPPED, FINISHED, BLOCKED_BY_BOUNDS, BLOCKED_BY_OCCUPANT
    }
}
