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
import fr.idev.mudserver.network.message.ingame.MovementBlockedByBounds;
import fr.idev.mudserver.network.message.ingame.MovementBlockedByOccupant;

/**
 * Fait avancer tous les personnages en déplacement d'une case à la fois, au
 * rythme de leur propre vitesse. La logique métier du déplacement (décompte des
 * cases, blocage, fin de mouvement) vit sur GameCharacter#updatePosition ; ce
 * ticker se contente de savoir QUI est en train de bouger (via les événements
 * CharacterStartedMoving/CharacterStoppedMoving) et d'envoyer les notifications
 * réseau correspondantes sur le pool de threads virtuels, afin de ne jamais
 * faire d'I/O bloquant sur ce thread partagé par tous les joueurs.
 */
@Component
public class MovementEngine extends Thread {

    private static final Logger log = LoggerFactory.getLogger(MovementEngine.class);

    private static final long TICK_INTERVAL_MS = 100L;

    private final Look lookAction;
    private final ExecutorService virtualThreadExecutor;
    private final Map<UUID, AbstractCharacter> movingCharacters = new ConcurrentHashMap<>();
    private final Map<UUID, CompletableFuture<Void>> pendingNotifications = new ConcurrentHashMap<>();

    public MovementEngine(Look lookAction, ExecutorService virtualThreadExecutor) {
        super("movement-ticker");
        this.lookAction = lookAction;
        this.virtualThreadExecutor = virtualThreadExecutor;
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
        switch (MovementSystem.updatePosition(character, now)) {
            case NO_MOVEMENT -> {
            }
            case STEPPED -> notifyMoved(character);
            case FINISHED -> {
                movingCharacters.remove(character.getId());
                notifyMoved(character);
            }
            case BLOCKED_BY_BOUNDS -> {
                movingCharacters.remove(character.getId());
                notifyAsync(character, () -> character.send(new MovementBlockedByBounds()));
            }
            case BLOCKED_BY_OCCUPANT -> {
                movingCharacters.remove(character.getId());
                notifyAsync(character, () -> character.send(new MovementBlockedByOccupant()));
            }
        }
    }

    // Le rafraîchissement du "look" nécessite une Connection, donc n'a de sens
    // que pour un GamePlayer ; pour un NPC/monstre en mouvement, ce serait un
    // no-op ici plutôt qu'un appel à retirer plus tard.
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
