package fr.idev.mudserver.game;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

import fr.idev.mudserver.domain.actor.AbstractObject;
import fr.idev.mudserver.domain.actor.component.IdentityComponent;
import fr.idev.mudserver.domain.actor.component.MovementComponent;
import fr.idev.mudserver.domain.actor.component.NetworkComponent;
import fr.idev.mudserver.domain.actor.component.PositionComponent;
import fr.idev.mudserver.domain.map.HexCoordinate;
import fr.idev.mudserver.domain.world.RoomInstance;
import jakarta.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import fr.idev.mudserver.controller.ingame.Look;
import fr.idev.mudserver.domain.actor.AbstractCharacter;
import fr.idev.mudserver.domain.actor.system.NetworkSystem;
import fr.idev.mudserver.network.message.ingame.MovementBlockedByBounds;
import fr.idev.mudserver.network.message.ingame.MovementBlockedByOccupant;

@Component
public class MovementSubsystem extends Thread {

    private static final Logger log = LoggerFactory.getLogger(MovementSubsystem.class);

    private static final long TICK_INTERVAL_MS = 100L;

    private final Look lookAction;
    private final ExecutorService virtualThreadExecutor;
    private final NetworkSystem networkSystem;
    private final ECS ecs;
    private final Map<UUID, CompletableFuture<Void>> pendingNotifications = new ConcurrentHashMap<>();

    public MovementSubsystem(Look lookAction, NetworkSystem networkSystem, ExecutorService virtualThreadExecutor,
            ECS ecs) {
        super("movement-ticker");
        this.lookAction = lookAction;
        this.networkSystem = networkSystem;
        this.virtualThreadExecutor = virtualThreadExecutor;
        this.ecs = ecs;
        setDaemon(true);
    }

    @Override
    public void run() {
        while (!isInterrupted()) {
            long tickStart = System.currentTimeMillis();
            tick(tickStart);
            long sleepTime = TICK_INTERVAL_MS - (System.currentTimeMillis() - tickStart);
            if (sleepTime > 0) {
                try {
                    Thread.sleep(sleepTime);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    void tick(long now) {

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

    @PreDestroy
    void shutdown() {
        interrupt();
    }
}
