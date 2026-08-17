package fr.idev.mudserver.domain.actor.system;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

import fr.idev.mudserver.controller.ingame.Look;
import fr.idev.mudserver.domain.actor.AbstractObject;
import fr.idev.mudserver.domain.actor.component.NetworkComponent;
import fr.idev.mudserver.domain.actor.component.PositionComponent;
import fr.idev.mudserver.game.ECS;
import fr.idev.mudserver.game.Query;
import fr.idev.mudserver.network.message.ingame.MovementBlockedByBounds;
import fr.idev.mudserver.network.message.ingame.MovementBlockedByOccupant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
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

    private static final Logger log = LoggerFactory.getLogger(MovementSystem.class);

    public static final int REFERENCE_SPEED = 5;
    public static final long REFERENCE_TIME_MS = 1000L;

    private final Look lookAction;
    private final ExecutorService virtualThreadExecutor;
    private final NetworkSystem networkSystem;
    private final ECS ecs;
    private final Map<UUID, CompletableFuture<Void>> pendingNotifications = new ConcurrentHashMap<>();

    public MovementSystem(Look lookAction, NetworkSystem networkSystem, ExecutorService virtualThreadExecutor,
            ECS ecs) {
        this.lookAction = lookAction;
        this.networkSystem = networkSystem;
        this.virtualThreadExecutor = virtualThreadExecutor;
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

            if (now - movementComponent.lastStepAt() < identityComponent.cellSpeed()) {
                continue;
            }

            HexCoordinate currentCoord = positionComponent.hexCoordinate();
            HexCoordinate nextCoord = currentCoord.neighbor(movementComponent.direction());
            RoomInstance room = positionComponent.currentRoom();
            if (!room.isInBounds(nextCoord)) {
                entity.detachComponent(MovementComponent.class);
                notifyAsync(entity, () -> networkSystem.send(entity, new MovementBlockedByBounds()));
                continue;
            }

            if (!room.tryClaimCell(nextCoord, (AbstractCharacter) entity)) {
                entity.detachComponent(MovementComponent.class);
                notifyAsync(entity, () -> networkSystem.send(entity, new MovementBlockedByOccupant()));
                continue;
            }

            room.releaseCell(currentCoord, (AbstractCharacter) entity); // not with an ECS system right now
            entity.updateComponent(PositionComponent.class, current -> new PositionComponent(room, nextCoord));

            int remaining = movementComponent.cellsRemaining() - 1;
            if (remaining <= 0) {
                entity.detachComponent(MovementComponent.class);
            } else {
                entity.updateComponent(MovementComponent.class, current -> current.withRemaining(remaining, now));
            }

            if (entity.findComponent(NetworkComponent.class).isEmpty()) {
                continue;
            }
            notifyAsync(entity, () -> lookAction.onReceive(entity.component(NetworkComponent.class).connection(), ""));
        }

    }

    private void notifyAsync(AbstractObject entity, Runnable notification) {
        pendingNotifications.compute(entity.getId(), (id, previous) -> {
            CompletableFuture<Void> previousStage = previous == null
                    ? CompletableFuture.completedFuture(null)
                    : previous;
            return previousStage.thenRunAsync(() -> runSafely(entity, notification), virtualThreadExecutor);
        });
    }

    private void runSafely(AbstractObject character, Runnable notification) {
        try {
            notification.run();
        } catch (RuntimeException e) {
            log.warn("Échec de l'envoi d'une notification de mouvement à {}", character.getId(), e);
        }
    }

}
