package fr.idev.mudserver.game;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

import fr.idev.mudserver.domain.actor.component.NetworkComponent;
import jakarta.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import fr.idev.mudserver.controller.ingame.Look;
import fr.idev.mudserver.domain.actor.AbstractCharacter;
import fr.idev.mudserver.domain.actor.instance.CharacterInstance;
import fr.idev.mudserver.domain.actor.event.CharacterStartedMoving;
import fr.idev.mudserver.domain.actor.event.CharacterStoppedMoving;
import fr.idev.mudserver.domain.actor.system.MovementSystem;
import fr.idev.mudserver.domain.actor.system.NetworkSystem;
import fr.idev.mudserver.network.message.ingame.MovementBlockedByBounds;
import fr.idev.mudserver.network.message.ingame.MovementBlockedByOccupant;

@Component
public class MovementSubsystem extends Thread {

    private static final Logger log = LoggerFactory.getLogger(MovementSubsystem.class);

    private static final long TICK_INTERVAL_MS = 100L;

    private final Look lookAction;
    private final ExecutorService virtualThreadExecutor;
    private final MovementSystem movementSystem;
    private final NetworkSystem networkSystem;
    private final Map<UUID, AbstractCharacter> movingCharacters = new ConcurrentHashMap<>();
    private final Map<UUID, CompletableFuture<Void>> pendingNotifications = new ConcurrentHashMap<>();

    public MovementSubsystem(Look lookAction, ExecutorService virtualThreadExecutor, MovementSystem movementSystem,
            NetworkSystem networkSystem) {
        super("movement-ticker");
        this.lookAction = lookAction;
        this.virtualThreadExecutor = virtualThreadExecutor;
        this.movementSystem = movementSystem;
        this.networkSystem = networkSystem;
        setDaemon(true);
    }

    @EventListener
    void onCharacterStartedMoving(CharacterStartedMoving event) {
        movingCharacters.put(event.character().getId(), event.character());
    }

    @EventListener
    void onCharacterStoppedMoving(CharacterStoppedMoving event) {
        movingCharacters.remove(event.character().getId());
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
        for (AbstractCharacter character : movingCharacters.values()) {
            processIfDue(character, now);
        }
    }

    private void processIfDue(AbstractCharacter character, long now) {
        switch (movementSystem.updatePosition(character, now)) {
            case NO_MOVEMENT -> {
            }
            case STEPPED -> notifyMoved(character);
            case FINISHED -> {
                movingCharacters.remove(character.getId());
                notifyMoved(character);
            }
            case BLOCKED_BY_BOUNDS -> {
                movingCharacters.remove(character.getId());
                notifyAsync(character, () -> networkSystem.send(character, new MovementBlockedByBounds()));
            }
            case BLOCKED_BY_OCCUPANT -> {
                movingCharacters.remove(character.getId());
                notifyAsync(character, () -> networkSystem.send(character, new MovementBlockedByOccupant()));
            }
        }
    }

    private void notifyMoved(AbstractCharacter character) {
        if (character instanceof CharacterInstance player) {
            notifyAsync(character,
                    () -> lookAction.onReceive(player.component(NetworkComponent.class).connection(), ""));
        }
    }

    private void notifyAsync(AbstractCharacter character, Runnable notification) {
        pendingNotifications.compute(character.getId(), (id, previous) -> {
            CompletableFuture<Void> previousStage = previous == null
                    ? CompletableFuture.completedFuture(null)
                    : previous;
            return previousStage.thenRunAsync(() -> runSafely(character, notification), virtualThreadExecutor);
        });
    }

    private void runSafely(AbstractCharacter character, Runnable notification) {
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
