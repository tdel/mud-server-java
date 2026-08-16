package fr.idev.mudserver.domain.actor;

import java.util.ArrayList;
import java.util.List;

import fr.idev.mudserver.domain.actor.event.CharacterStartedMoving;
import fr.idev.mudserver.domain.actor.event.CharacterStoppedMoving;
import fr.idev.mudserver.domain.actor.event.DomainEventPublisher;
import fr.idev.mudserver.domain.map.HexCoordinate;
import fr.idev.mudserver.domain.map.HexDirection;
import fr.idev.mudserver.domain.world.RoomInstance;

public final class CharacterMovementSystem {

    private final AbstractCharacter character;
    private volatile ActiveMovement activeMovement;

    public CharacterMovementSystem(AbstractCharacter character) {
        this.character = character;
    }

    public CellStepOutcome moveOneCell(HexDirection direction) {
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

    public List<HexCoordinate> remainingPath() {
        ActiveMovement movement = this.activeMovement; // photo unique du champ volatile
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

    public void startMovement(HexDirection direction, int cellsRequested) {
        this.activeMovement = new ActiveMovement(direction, cellsRequested, System.currentTimeMillis());
        DomainEventPublisher.publish(new CharacterStartedMoving(character));
    }

    public void stopMovement() {
        if (this.activeMovement == null) {
            return;
        }
        this.activeMovement = null;
        DomainEventPublisher.publish(new CharacterStoppedMoving(character));
    }

    public MovementStepOutcome updatePosition(long now) {
        ActiveMovement movement = this.activeMovement;
        if (movement == null || now - movement.lastStepAt() < character.getMillisPerCell()) {
            return MovementStepOutcome.NO_MOVEMENT;
        }

        CellStepOutcome step = moveOneCell(movement.direction());
        if (!step.moved()) {
            this.activeMovement = null;
            return step.blockedByOccupant()
                    ? MovementStepOutcome.BLOCKED_BY_OCCUPANT
                    : MovementStepOutcome.BLOCKED_BY_BOUNDS;
        }

        int remaining = movement.cellsRemaining() - 1;
        if (remaining <= 0) {
            this.activeMovement = null;
            return MovementStepOutcome.FINISHED;
        }
        this.activeMovement = movement.withRemaining(remaining, now);
        return MovementStepOutcome.STEPPED;
    }

    public record CellStepOutcome(boolean moved, boolean blockedByBounds, boolean blockedByOccupant) {
    }

    private record ActiveMovement(HexDirection direction, int cellsRemaining, long lastStepAt) {
        ActiveMovement withRemaining(int newRemaining, long stepAt) {
            return new ActiveMovement(direction, newRemaining, stepAt);
        }
    }

    public enum MovementStepOutcome {
        NO_MOVEMENT, STEPPED, FINISHED, BLOCKED_BY_BOUNDS, BLOCKED_BY_OCCUPANT
    }
}
