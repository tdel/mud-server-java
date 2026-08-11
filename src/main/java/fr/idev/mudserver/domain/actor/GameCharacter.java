package fr.idev.mudserver.domain.actor;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import fr.idev.mudserver.domain.HexCoordinate;
import fr.idev.mudserver.domain.HexDirection;
import fr.idev.mudserver.domain.RoomInstance;
import fr.idev.mudserver.domain.RoomPortal;
import fr.idev.mudserver.game.dice.DiceExpression;
import fr.idev.mudserver.game.dice.DiceRoller;

public abstract sealed class GameCharacter extends GameObject permits GamePlayer, GameMonster, GameNpc {

    public static final int DEFAULT_SPEED = 6;

    private final Map<Attribute, Integer> attributes;
    private int currentHealth;
    private int maxHealth;

    private RoomInstance currentRoom;
    private HexCoordinate position;
    protected int speed = DEFAULT_SPEED;
    private volatile CombatEncounter encounter;
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
        int budget = Math.min(requestedCells, getSpeed());
        RoomInstance room = getCurrentRoom();
        HexCoordinate current = getPosition();

        int cellsMoved = 0;
        boolean blockedByOccupant = false;
        boolean crossedPortal = false;
        boolean triggeredCombat = false;

        for (int i = 0; i < budget; i++) {
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

            Optional<RoomPortal> portal = room.findPortalAt(current);
            if (portal.isPresent()) {
                crossedPortal = crossPortal(portal.get());
                break;
            }
        }

        boolean blockedByBounds = cellsMoved == 0 && !blockedByOccupant;
        return new MovementOutcome(cellsMoved, blockedByBounds, blockedByOccupant, crossedPortal, triggeredCombat);
    }

    protected boolean crossPortal(RoomPortal portal) {
        return false;
    }

    protected boolean onEnteredCell(HexCoordinate cell) {
        return false;
    }

    public record MovementOutcome(int cellsMoved, boolean blockedByBounds, boolean blockedByOccupant,
            boolean crossedPortal, boolean triggeredCombat) {
    }
}
