package fr.idev.mudserver.game.engine;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import fr.idev.mudserver.domain.map.HexCoordinate;
import fr.idev.mudserver.domain.map.HexDirection;
import fr.idev.mudserver.domain.world.ZoneInstance;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import fr.idev.mudserver.domain.actor.AbstractCharacter;
import fr.idev.mudserver.network.message.ingame.MovementBlockedByBounds;
import fr.idev.mudserver.network.message.ingame.MovementBlockedByOccupant;
import fr.idev.mudserver.network.message.ingame.ViewAround;

@Component
public class MovementEngine {

    public static final int REFERENCE_SPEED = 5;
    public static final long REFERENCE_TIME_MS = 1000L;

    private static final Logger log = LoggerFactory.getLogger(MovementEngine.class);

    private static final long TICK_INTERVAL_MS = 100L;

    private final Map<UUID, AbstractCharacter> movingCharacters = new ConcurrentHashMap<>();

    public void startMovement(HexDirection direction, int cellsRequested, AbstractCharacter character) {
        character.activeMovement = new ActiveMovement(direction, cellsRequested, System.currentTimeMillis());
        movingCharacters.put(character.getId(), character);
    }

    public void stopMovement(AbstractCharacter character) {
        if (character.activeMovement == null) {
            return;
        }
        character.activeMovement = null;
        movingCharacters.remove(character.getId());
    }

    @Scheduled(fixedRate = TICK_INTERVAL_MS)
    void tick() {
        long now = System.currentTimeMillis();
        for (AbstractCharacter character : movingCharacters.values()) {
            switch (updatePosition(character, now)) {
                case NO_MOVEMENT -> {
                }
                case STEPPED -> {
                    character.send(new ViewAround(character));
                }
                case FINISHED -> {
                    movingCharacters.remove(character.getId());
                    character.send(new ViewAround(character));

                }
                case BLOCKED_BY_BOUNDS -> {
                    movingCharacters.remove(character.getId());
                    character.send(new MovementBlockedByBounds());
                }
                case BLOCKED_BY_OCCUPANT -> {
                    movingCharacters.remove(character.getId());
                    character.send(new MovementBlockedByOccupant());
                }
            }
        }
    }
    private MovementStepOutcome updatePosition(AbstractCharacter character, long now) {
        ActiveMovement movement = character.activeMovement;
        if (movement == null || now - movement.lastStepAt() < getMillisPerCell(character)) {
            return MovementStepOutcome.NO_MOVEMENT;
        }

        CellStepOutcome step = move(character, movement.direction());
        if (!step.moved()) {
            character.activeMovement = null;
            return step.blockedByOccupant()
                    ? MovementStepOutcome.BLOCKED_BY_OCCUPANT
                    : MovementStepOutcome.BLOCKED_BY_BOUNDS;
        }

        int remaining = movement.cellsRemaining() - 1;
        if (remaining <= 0) {
            character.activeMovement = null;
            return MovementStepOutcome.FINISHED;
        }

        character.activeMovement = movement.withRemaining(remaining, now);

        return MovementStepOutcome.STEPPED;
    }

    private CellStepOutcome move(AbstractCharacter character, HexDirection direction) {
        ZoneInstance zone = character.getCurrentZone();
        HexCoordinate current = character.getPosition();
        HexCoordinate next = current.neighbor(direction);

        if (!zone.isInBounds(next)) {
            return new CellStepOutcome(false, true, false);
        }
        if (!zone.tryClaimCell(next, character)) {
            return new CellStepOutcome(false, false, true);
        }

        zone.releaseCell(current, character);
        character.setPosition(next);

        return new CellStepOutcome(true, false, false);
    }

    private long getMillisPerCell(AbstractCharacter character) {
        return REFERENCE_TIME_MS * REFERENCE_SPEED / Math.max(1, character.getSpeed());
    }

    public record CellStepOutcome(boolean moved, boolean blockedByBounds, boolean blockedByOccupant) {
    }

    public record ActiveMovement(HexDirection direction, int cellsRemaining, long lastStepAt) {
        ActiveMovement withRemaining(int newRemaining, long stepAt) {
            return new ActiveMovement(direction, newRemaining, stepAt);
        }
    }

    public enum MovementStepOutcome {
        NO_MOVEMENT, STEPPED, FINISHED, BLOCKED_BY_BOUNDS, BLOCKED_BY_OCCUPANT
    }
}
