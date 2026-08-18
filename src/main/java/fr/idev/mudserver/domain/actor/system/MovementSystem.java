package fr.idev.mudserver.domain.actor.system;

import java.util.*;

import fr.idev.mudserver.domain.actor.AbstractObject;
import fr.idev.mudserver.domain.actor.component.PositionComponent;
import fr.idev.mudserver.game.ECS;
import fr.idev.mudserver.game.Query;
import fr.idev.mudserver.network.message.ingame.MovementBlockedByBounds;
import fr.idev.mudserver.network.message.ingame.MovementBlockedByOccupant;
import fr.idev.mudserver.network.message.ingame.ViewAround;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import fr.idev.mudserver.domain.actor.AbstractCharacter;
import fr.idev.mudserver.domain.actor.component.IdentityComponent;
import fr.idev.mudserver.domain.actor.component.MovementComponent;
import fr.idev.mudserver.domain.map.HexCoordinate;
import fr.idev.mudserver.domain.world.RoomInstance;

@Service
public class MovementSystem {

    public static final int REFERENCE_SPEED = 5;
    public static final long REFERENCE_TIME_MS = 1000L;

    private final NetworkSystem networkSystem;
    private final ECS ecs;

    public MovementSystem(NetworkSystem networkSystem, ECS ecs) {
        this.networkSystem = networkSystem;
        this.ecs = ecs;
    }

    @Scheduled(fixedRate = 100)
    public void update() {
        long now = System.currentTimeMillis();
        Query q = ecs.createQuery();
        q.addRequirement(MovementComponent.class);
        q.addRequirement(IdentityComponent.class);
        q.addRequirement(PositionComponent.class);

        List<AbstractObject> entities = ecs.execute(q);
        for (AbstractObject entity : entities) {
            MovementComponent movementComponent = entity.component(MovementComponent.class);
            IdentityComponent identityComponent = entity.component(IdentityComponent.class);
            PositionComponent positionComponent = entity.component(PositionComponent.class);

            if (now - movementComponent.lastStepAt < identityComponent.cellSpeed()) {
                continue;
            }

            HexCoordinate currentCoord = positionComponent.hexCoordinate;
            HexCoordinate nextCoord = currentCoord.neighbor(movementComponent.direction);
            RoomInstance room = positionComponent.currentRoom;
            if (!room.isInBounds(nextCoord)) {
                entity.detachComponent(MovementComponent.class);
                networkSystem.send(entity, new MovementBlockedByBounds());
                continue;
            }

            if (!room.tryClaimCell(nextCoord, (AbstractCharacter) entity)) {
                entity.detachComponent(MovementComponent.class);
                networkSystem.send(entity, new MovementBlockedByOccupant());
                continue;
            }

            room.releaseCell(currentCoord, (AbstractCharacter) entity); // not with an ECS system right now
            synchronized (entity) {
                positionComponent.currentRoom = room;
                positionComponent.hexCoordinate = nextCoord;
            }

            int remaining = movementComponent.cellsRemaining - 1;
            if (remaining <= 0) {
                entity.detachComponent(MovementComponent.class);
            } else {
                synchronized (entity) {
                    movementComponent.cellsRemaining = remaining;
                    movementComponent.lastStepAt = now;
                }
            }

            networkSystem.send(entity, new ViewAround(entity));
        }

    }

}
