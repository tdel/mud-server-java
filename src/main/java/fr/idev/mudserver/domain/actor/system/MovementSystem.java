package fr.idev.mudserver.domain.actor.system;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import fr.idev.mudserver.domain.actor.AbstractObject;
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

}
