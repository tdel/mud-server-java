package fr.idev.mudserver.domain.actor;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

import fr.idev.mudserver.domain.HexCoordinate;
import fr.idev.mudserver.domain.HexDirection;
import fr.idev.mudserver.domain.RoomInstance;
import fr.idev.mudserver.domain.actor.event.CharacterStartedMoving;
import fr.idev.mudserver.domain.actor.event.CharacterStoppedMoving;
import fr.idev.mudserver.domain.actor.event.DomainEventPublisher;
import fr.idev.mudserver.game.dice.DiceExpression;
import fr.idev.mudserver.game.dice.DiceRoller;
import fr.idev.mudserver.network.OutputMessage;

public abstract sealed class GameCharacter extends GameObject permits GamePlayer, GameMonster, GameNpc {

    public static final int DEFAULT_SPEED = 6;
    public static final int REFERENCE_SPEED = 5;
    public static final long REFERENCE_TIME_MS = 1000L;

    private final Map<Attribute, Integer> attributes;
    private int currentHealth;
    private int maxHealth;

    private volatile RoomInstance currentRoom;
    private volatile HexCoordinate position;
    protected int speed = DEFAULT_SPEED;
    private volatile CombatEncounter encounter;
    private volatile ActiveMovement activeMovement;
    private final ActionEconomy actionEconomy = new ActionEconomy();

    protected GameCharacter(UUID id, String name, Map<Attribute, Integer> attributes, int currentHealth,
            int maxHealth) {
        super(id, name);
        this.attributes = new EnumMap<>(attributes);
        this.currentHealth = currentHealth;
        this.maxHealth = maxHealth;
    }

    public int getAttribute(Attribute attribute) {
        return attributes.get(attribute);
    }

    public int getModifier(Attribute attribute) {
        return Math.floorDiv(getAttribute(attribute) - 10, 2);
    }

    public int getArmorClass() {
        return 10 + getModifier(Attribute.DEXTERITY);
    }

    public Map<Attribute, Integer> getAttributes() {
        return Map.copyOf(attributes);
    }

    public int getCurrentHealth() {
        return currentHealth;
    }

    public void setCurrentHealth(int currentHealth) {
        this.currentHealth = currentHealth;
    }

    public int getMaxHealth() {
        return maxHealth;
    }

    public void setMaxHealth(int maxHealth) {
        this.maxHealth = maxHealth;
    }

    public int heal(int amount) {
        int healed = Math.min(amount, maxHealth - currentHealth);
        currentHealth += healed;
        return healed;
    }

    public RoomInstance getCurrentRoom() {
        return currentRoom;
    }

    public void setCurrentRoom(RoomInstance currentRoom) {
        this.currentRoom = currentRoom;
    }

    public HexCoordinate getPosition() {
        return position;
    }

    public void setPosition(HexCoordinate position) {
        this.position = position;
    }

    public int getSpeed() {
        return speed;
    }

    public void setSpeed(int speed) {
        this.speed = speed;
    }

    public long getMillisPerCell() {
        return REFERENCE_TIME_MS * REFERENCE_SPEED / Math.max(1, getSpeed());
    }

    public boolean isInCombat() {
        return encounter != null;
    }

    public CombatEncounter getEncounter() {
        return encounter;
    }

    public void setEncounter(CombatEncounter encounter) {
        this.encounter = encounter;
    }

    public ActionEconomy getActionEconomy() {
        return actionEconomy;
    }

    public int rollInitiative() {
        return DiceRoller.roll(new DiceExpression(1, 20, getModifier(Attribute.DEXTERITY))).total();
    }

    public MovementOutcome moveToCell(HexDirection direction, int requestedCells) {
        RoomInstance room = getCurrentRoom();
        HexCoordinate current = getPosition();

        int cellsMoved = 0;
        boolean blockedByOccupant = false;
        boolean triggeredCombat = false;

        for (int i = 0; i < requestedCells; i++) {
            HexCoordinate next = current.neighbor(direction);
            if (!room.isInBounds(next)) {
                break;
            }
            if (!room.tryClaimCell(next, this)) {
                blockedByOccupant = true;
                break;
            }
            room.releaseCell(current, this);
            setPosition(next);
            current = next;
            cellsMoved++;

            if (onEnteredCell(current)) {
                triggeredCombat = true;
                break;
            }
        }

        boolean blockedByBounds = cellsMoved == 0 && !blockedByOccupant;
        return new MovementOutcome(cellsMoved, blockedByBounds, blockedByOccupant, triggeredCombat);
    }

    // Avance d'une case sans déclencher de conséquence (combat, portail) :
    // MovementTicker s'en charge après coup.
    public CellStepOutcome moveOneCell(HexDirection direction) {
        RoomInstance room = getCurrentRoom();
        HexCoordinate current = getPosition();
        HexCoordinate next = current.neighbor(direction);

        if (!room.isInBounds(next)) {
            return new CellStepOutcome(false, true, false);
        }
        if (!room.tryClaimCell(next, this)) {
            return new CellStepOutcome(false, false, true);
        }

        room.releaseCell(current, this);
        setPosition(next);

        return new CellStepOutcome(true, false, false);
    }

    public boolean onEnteredCell(HexCoordinate cell) {
        return false;
    }

    public void startMovement(HexDirection direction, int cellsRequested) {
        this.activeMovement = new ActiveMovement(direction, cellsRequested, System.currentTimeMillis());
        DomainEventPublisher.publish(new CharacterStartedMoving(this));
    }

    public void stopMovement() {
        if (this.activeMovement == null) {
            return;
        }
        this.activeMovement = null;
        DomainEventPublisher.publish(new CharacterStoppedMoving(this));
    }

    public MovementStepOutcome updatePosition(long now) {
        ActiveMovement movement = this.activeMovement;
        if (movement == null || now - movement.lastStepAt() < getMillisPerCell()) {
            return MovementStepOutcome.NO_MOVEMENT;
        }

        CellStepOutcome step = moveOneCell(movement.direction());
        if (!step.moved()) {
            this.activeMovement = null;
            return step.blockedByOccupant()
                    ? MovementStepOutcome.BLOCKED_BY_OCCUPANT
                    : MovementStepOutcome.BLOCKED_BY_BOUNDS;
        }

        int remaining = movement.cellsRemaining() - 1;
        if (remaining <= 0) {
            this.activeMovement = null;
            return MovementStepOutcome.FINISHED;
        }
        this.activeMovement = movement.withRemaining(remaining, now);
        return MovementStepOutcome.STEPPED;
    }

    // No-op par défaut : seul GamePlayer a une Connection à notifier.
    public void send(OutputMessage message) {
    }

    public record MovementOutcome(int cellsMoved, boolean blockedByBounds, boolean blockedByOccupant,
            boolean triggeredCombat) {
    }

    public record CellStepOutcome(boolean moved, boolean blockedByBounds, boolean blockedByOccupant) {
    }

    private record ActiveMovement(HexDirection direction, int cellsRemaining, long lastStepAt) {
        ActiveMovement withRemaining(int newRemaining, long stepAt) {
            return new ActiveMovement(direction, newRemaining, stepAt);
        }
    }

    public enum MovementStepOutcome {
        NO_MOVEMENT, STEPPED, FINISHED, BLOCKED_BY_BOUNDS, BLOCKED_BY_OCCUPANT
    }
}
