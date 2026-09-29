package app.game.engine;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import app.domain.map.Position;
import app.game.engine.ContinuousStep.StepResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import app.domain.actor.AbstractCharacter;
import app.domain.actor.event.DomainEventPublisher;
import app.domain.actor.event.CharacterStartedMoving;
import app.domain.actor.event.CharacterBeginAttack;
import app.domain.actor.event.CharacterDied;
import app.domain.actor.instance.PlayerInstance;
import app.network.message.ingame.CharacterMovementBlocked;
import app.network.message.ingame.CharacterMovementFinished;
import app.network.message.ingame.CharacterMovementStopped;
import app.network.message.ingame.MovementBlockedByBounds;
import app.network.message.ingame.MovementFinished;
import app.network.message.ingame.MovementStopped;

@Component
public class MovementEngine {

    // speed est le run speed L2 (Human ~110, monstres ~55-75, cf. race.xml /
    // monsters.xml) — pas une distance de case DnD5e. Le diviseur convertit
    // cette valeur "unités L2" en cases de la CollisionGrid par seconde, calibré
    // pour garder le rythme de déplacement déjà éprouvé sur la grille (un Human à
    // 110 fait ~2.44 case/s).
    public static final double SPEED_DIVISOR = 45.0;

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
            character.getMotionSystem().updateMovement(new ActiveMovement(List.copyOf(waypoints), System.nanoTime()));
            character.getMotionSystem()
                    .setHeading(character.getMotionSystem().getPosition().headingTo(waypoints.get(0)));
            movingCharacters.put(character.getId(), character);
        }
        log.debug("movement.started thread={} character={} waypoints={}", Thread.currentThread().getName(),
                character.getId(), waypoints.size());
        // Hors du verrou : un listener (ActiveEffectEngine, fin de Relax) peut
        // diffuser des messages.
        DomainEventPublisher.publish(new CharacterStartedMoving(character));
    }

    /**
     * Fait avancer le déplacement en cours jusqu'à maintenant plutôt que jusqu'au
     * dernier tick (jusqu'à TICK_INTERVAL_MS de retard), sans le terminer : la fin
     * (MovementFinished/Blocked) reste annoncée par le tick. Appelé avant de
     * recalculer un chemin (`goto` en pleine course) ou d'arrêter le personnage :
     * sans ça, le nouveau chemin partait d'une position périmée et le temps écoulé
     * depuis le tick était perdu — le serveur prenait du retard sur le client à
     * chaque clic, que le client finissait par rattraper d'un coup (saccade).
     */
    public void settle(AbstractCharacter character) {
        synchronized (character) {
            ActiveMovement movement = character.getMotionSystem().getActiveMovement();
            if (movement == null) {
                return;
            }
            long now = System.nanoTime();
            Position previous = character.getMotionSystem().getPosition();
            StepResult result = stepTo(character, movement, now);
            character.getMotionSystem().setPosition(result.position());
            if (!result.position().equals(previous)) {
                character.getMotionSystem().setHeading(previous.headingTo(result.position()));
            }
            // Destination atteinte : garde un waypoint sur place pour que le prochain tick
            // annonce la fin comme d'habitude.
            List<Position> remaining = result.remainingWaypoints().isEmpty()
                    ? List.of(result.position())
                    : result.remainingWaypoints();
            character.getMotionSystem().updateMovement(movement.withRemaining(remaining, now));
        }
    }

    /**
     * Position à l'instant présent, projetée le long du déplacement en cours sans
     * rien modifier (voir settle) : c'est elle que le client compare à la sienne
     * pour corriger sa dérive (commande `position`).
     */
    public Position currentPosition(AbstractCharacter character) {
        synchronized (character) {
            ActiveMovement movement = character.getMotionSystem().getActiveMovement();
            if (movement == null) {
                return character.getMotionSystem().getPosition();
            }
            return stepTo(character, movement, System.nanoTime()).position();
        }
    }

    private StepResult stepTo(AbstractCharacter character, ActiveMovement movement, long now) {
        double dtSeconds = (now - movement.lastTickAtNanos()) / 1_000_000_000.0;
        return ContinuousStep.step(character.getMotionSystem().getPosition(), movement.remainingWaypoints(),
                unitsPerSecond(character.getMotionSystem().getSpeed()), dtSeconds,
                character.getMotionSystem().getCurrentMap().getCollisionGrid());
    }

    public void stopMovement(AbstractCharacter character) {
        synchronized (character) {
            if (character.getMotionSystem().getActiveMovement() == null) {
                return;
            }
            settle(character);
            character.getMotionSystem().clearMovement();
            movingCharacters.remove(character.getId());
        }
        log.debug("movement.stopped thread={} character={}", Thread.currentThread().getName(), character.getId());
        character.send(new MovementStopped(character.getMotionSystem().getPosition().x(),
                character.getMotionSystem().getPosition().y()));
        character.broadcast(
                new CharacterMovementStopped(character.getId(), character.getName(),
                        character.getMotionSystem().getPosition().x(), character.getMotionSystem().getPosition().y()),
                character instanceof PlayerInstance player ? player : null);
    }

    @EventListener
    void onCharacterDied(CharacterDied event) {
        stopMovement(event.character());
    }

    @EventListener
    void onCharacterBeginAttack(CharacterBeginAttack event) {
        if (event.stopsAttacker()) {
            stopMovement(event.attacker());
        }
    }

    @Scheduled(fixedRate = TICK_INTERVAL_MS)
    void tick() {
        long now = System.nanoTime();
        for (AbstractCharacter character : movingCharacters.values()) {
            try {
                MovementStepOutcome outcome = updatePosition(character, now);
                if (outcome != MovementStepOutcome.NO_MOVEMENT) {
                    character.getKnownList().refresh();
                }
                switch (outcome) {
                    case NO_MOVEMENT -> {
                    }
                    case STEPPED -> {
                        // Plus d'envoi de position à chaque tick (~100ms) : le client interpole
                        // localement son propre déplacement et celui des autres personnages de la
                        // map à partir de la cible (CharacterMovementStarted/MovementStarted) et de
                        // sa vitesse, et corrige la dérive via la commande "position" à la demande
                        // (voir Position.java) plutôt que via un flux poussé par le serveur.
                    }
                    case FINISHED -> {
                        movingCharacters.remove(character.getId());
                        log.debug("movement.finished thread={} character={}", Thread.currentThread().getName(),
                                character.getId());
                        character.send(new MovementFinished(character.getMotionSystem().getPosition().x(),
                                character.getMotionSystem().getPosition().y()));
                        if (character instanceof PlayerInstance player) {
                            character.broadcast(new CharacterMovementFinished(character.getId(), character.getName(),
                                    character.getMotionSystem().getPosition().x(),
                                    character.getMotionSystem().getPosition().y()), player);
                        }
                    }
                    case BLOCKED_BY_BOUNDS -> {
                        movingCharacters.remove(character.getId());
                        log.debug("movement.blocked thread={} character={}", Thread.currentThread().getName(),
                                character.getId());
                        character.send(new MovementBlockedByBounds(character.getMotionSystem().getPosition().x(),
                                character.getMotionSystem().getPosition().y()));
                        if (character instanceof PlayerInstance player) {
                            character.broadcast(new CharacterMovementBlocked(character.getId(), character.getName(),
                                    character.getMotionSystem().getPosition().x(),
                                    character.getMotionSystem().getPosition().y()), player);
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
            ActiveMovement movement = character.getMotionSystem().getActiveMovement();
            if (movement == null) {
                return MovementStepOutcome.NO_MOVEMENT;
            }

            Position previous = character.getMotionSystem().getPosition();
            StepResult result = stepTo(character, movement, now);
            character.getMotionSystem().setPosition(result.position());
            if (!result.position().equals(previous)) {
                character.getMotionSystem().setHeading(previous.headingTo(result.position()));
            }

            if (result.blocked()) {
                character.getMotionSystem().clearMovement();
                return MovementStepOutcome.BLOCKED_BY_BOUNDS;
            }

            if (result.remainingWaypoints().isEmpty()) {
                character.getMotionSystem().clearMovement();
                return MovementStepOutcome.FINISHED;
            }

            character.getMotionSystem().updateMovement(movement.withRemaining(result.remainingWaypoints(), now));
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
