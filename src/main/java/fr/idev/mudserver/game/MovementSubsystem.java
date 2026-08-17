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
import fr.idev.mudserver.domain.actor.system.MovementSystem;
import fr.idev.mudserver.domain.map.HexCoordinate;
import fr.idev.mudserver.domain.world.RoomInstance;
import jakarta.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class MovementSubsystem extends Thread {

    private static final Logger log = LoggerFactory.getLogger(MovementSubsystem.class);
    private static final long TICK_INTERVAL_MS = 100L;

    private final MovementSystem movementSystem;

    public MovementSubsystem(MovementSystem movementSystem) {
        super("movement-ticker");
        this.movementSystem = movementSystem;
        setDaemon(true);
    }

    @Override
    public void run() {
        while (!isInterrupted()) {
            long tickStart = System.currentTimeMillis();
            movementSystem.update(tickStart);
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

    @PreDestroy
    void shutdown() {
        interrupt();
    }
}
