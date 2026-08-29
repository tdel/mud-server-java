package app.game.engine;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import app.domain.map.Position;
import app.domain.world.CollisionGrid;
import app.domain.world.ZoneInstance;
import app.game.engine.ContinuousStep.StepResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import app.domain.actor.AbstractCharacter;
import app.domain.actor.event.AttackBegin;
import app.domain.actor.event.GamePlayerDied;
import app.domain.actor.instance.CharacterInstance;
import app.network.message.ingame.CharacterMovementBlocked;
import app.network.message.ingame.CharacterMovementFinished;
import app.network.message.ingame.CharacterMovementStopped;
import app.network.message.ingame.MovementBlockedByBounds;
import app.network.message.ingame.MovementFinished;
import app.network.message.ingame.MovementStopped;

@Component
public class MovementEngine {

    public static final double SPEED_DIVISOR = 5.0;

    private static final Logger log = LoggerFactory.getLogger(MovementEngine.class);

    private static final long TICK_INTERVAL_MS = 100L;

    private final Map<UUID, AbstractCharacter> movingCharacters = new ConcurrentHashMap<>();

    public static double unitsPerSecond(int speed) {
        return Math.max(1, speed) / SPEED_DIVISOR;
    }

    public void startMovement(List<Position> waypoints, AbstractCharacter character) {
        if (waypoints.isEmpty()) {
            return;
        }
        synchronized (character) {
            character.activeMovement = new ActiveMovement(List.copyOf(waypoints), System.nanoTime());
            movingCharacters.put(character.getId(), character);
        }
        log.debug("movement.started thread={} character={} waypoints={}", Thread.currentThread().getName(),
                character.getId(), waypoints.size());
    }

    public void stopMovement(AbstractCharacter character) {
        synchronized (character) {
            if (character.activeMovement == null) {
                return;
            }
            character.activeMovement = null;
            movingCharacters.remove(character.getId());
        }
        log.debug("movement.stopped thread={} character={}", Thread.currentThread().getName(), character.getId());
    }

    @EventListener
    void onGamePlayerDied(GamePlayerDied event) {
        CharacterInstance character = event.character();
        if (character.activeMovement == null) {
            return;
        }
        stopMovement(character);
        character.getCurrentZone().broadcast(new CharacterMovementStopped(character.getId(), character.getName(),
                character.getPosition().x(), character.getPosition().y()), null);
    }

    @EventListener
    void onAttackBegin(AttackBegin event) {
        CharacterInstance character = event.attacker();
        if (character.activeMovement == null) {
            return;
        }
        stopMovement(character);
        character.send(new MovementStopped(character.getPosition().x(), character.getPosition().y()));
        character.getCurrentZone().broadcast(new CharacterMovementStopped(character.getId(), character.getName(),
                character.getPosition().x(), character.getPosition().y()), character);
    }

    @Scheduled(fixedRate = TICK_INTERVAL_MS)
    void tick() {
        long now = System.nanoTime();
        for (AbstractCharacter character : movingCharacters.values()) {
            try {
                switch (updatePosition(character, now)) {
                    case NO_MOVEMENT -> {
                    }
                    case STEPPED -> {
                        // Plus d'envoi de position à chaque tick (~100ms) : le client interpole
                        // localement son propre déplacement et celui des autres personnages de la
                        // zone à partir de la cible (CharacterMovementStarted/MovementStarted) et de
                        // sa vitesse, et corrige la dérive via la commande "position" à la demande
                        // (voir Position.java) plutôt que via un flux poussé par le serveur.
                    }
                    case FINISHED -> {
                        movingCharacters.remove(character.getId());
                        log.debug("movement.finished thread={} character={}", Thread.currentThread().getName(),
                                character.getId());
                        character.send(new MovementFinished(character.getPosition().x(), character.getPosition().y()));
                        if (character instanceof CharacterInstance player) {
                            character.getCurrentZone().broadcast(new CharacterMovementFinished(character.getId(),
                                    character.getName(), character.getPosition().x(), character.getPosition().y()),
                                    player);
                        }
                    }
                    case BLOCKED_BY_BOUNDS -> {
                        movingCharacters.remove(character.getId());
                        log.debug("movement.blocked thread={} character={}", Thread.currentThread().getName(),
                                character.getId());
                        character.send(
                                new MovementBlockedByBounds(character.getPosition().x(), character.getPosition().y()));
                        if (character instanceof CharacterInstance player) {
                            character.getCurrentZone().broadcast(new CharacterMovementBlocked(character.getId(),
                                    character.getName(), character.getPosition().x(), character.getPosition().y()),
                                    player);
                        }
                    }
                }
            } catch (Exception e) {
                // Le personnage a pu être déconnecté (position remise à null) sans que son
                // mouvement en cours ait été arrêté ; on l'enlève pour éviter de replanter
                // à chaque tick, plutôt que de laisser l'exception interrompre la boucle
                // pour les autres personnages en mouvement.
                movingCharacters.remove(character.getId());
                log.error("movement.tick_failed character={}", character.getId(), e);
            }
        }
    }

    private MovementStepOutcome updatePosition(AbstractCharacter character, long now) {
        synchronized (character) {
            ActiveMovement movement = character.activeMovement;
            if (movement == null) {
                return MovementStepOutcome.NO_MOVEMENT;
            }

            ZoneInstance zone = character.getCurrentZone();
            CollisionGrid grid = zone.getCollisionGrid();
            double dtSeconds = (now - movement.lastTickAtNanos()) / 1_000_000_000.0;

            StepResult result = ContinuousStep.step(character.getPosition(), movement.remainingWaypoints(),
                    unitsPerSecond(character.getSpeed()), dtSeconds, grid);
            character.setPosition(result.position());

            if (result.blocked()) {
                character.activeMovement = null;
                return MovementStepOutcome.BLOCKED_BY_BOUNDS;
            }

            if (result.remainingWaypoints().isEmpty()) {
                character.activeMovement = null;
                return MovementStepOutcome.FINISHED;
            }

            character.activeMovement = movement.withRemaining(result.remainingWaypoints(), now);
            return MovementStepOutcome.STEPPED;
        }
    }

    public record ActiveMovement(List<Position> remainingWaypoints, long lastTickAtNanos) {
        ActiveMovement withRemaining(List<Position> newRemaining, long tickAtNanos) {
            return new ActiveMovement(newRemaining, tickAtNanos);
        }
    }

    public enum MovementStepOutcome {
        NO_MOVEMENT, STEPPED, FINISHED, BLOCKED_BY_BOUNDS
    }
}
