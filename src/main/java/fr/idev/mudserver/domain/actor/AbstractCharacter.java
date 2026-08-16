package fr.idev.mudserver.domain.actor;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

import fr.idev.mudserver.domain.actor.component.HealthComponent;
import fr.idev.mudserver.domain.map.HexCoordinate;
import fr.idev.mudserver.domain.actor.system.InventorySystem;
import fr.idev.mudserver.domain.actor.system.MovementSystem;
import fr.idev.mudserver.domain.world.RoomInstance;
import fr.idev.mudserver.domain.combat.ActionEconomy;
import fr.idev.mudserver.domain.combat.CombatEncounter;
import fr.idev.mudserver.game.dice.DiceExpression;
import fr.idev.mudserver.game.dice.DiceRoller;
import fr.idev.mudserver.network.OutputMessage;

public abstract class AbstractCharacter extends AbstractObject {

    public static final int DEFAULT_SPEED = 6;
    public static final int REFERENCE_SPEED = 5;
    public static final long REFERENCE_TIME_MS = 1000L;

    private final Map<Attribute, Integer> attributes;

    private volatile RoomInstance currentRoom;
    private volatile HexCoordinate position;
    protected int speed = DEFAULT_SPEED;
    private volatile CombatEncounter encounter;
    private final ActionEconomy actionEconomy = new ActionEconomy();
    private final MovementSystem movementSystem = new MovementSystem(this);

    protected AbstractCharacter(UUID id, String name, Map<Attribute, Integer> attributes, int currentHealth,
            int maxHealth) {
        super(id, name);
        this.attributes = new EnumMap<>(attributes);
        this.attachComponent(new HealthComponent(currentHealth, maxHealth));
    }

    public int getAttribute(Attribute attribute) {
        return attributes.get(attribute);
    }

    public int getModifier(Attribute attribute) {
        return Math.floorDiv(getAttribute(attribute) - 10, 2);
    }

    public int getArmorClass() {
        return InventorySystem.getArmorClass(this);
    }

    public Map<Attribute, Integer> getAttributes() {
        return Map.copyOf(attributes);
    }

    public int getCurrentHealth() {
        return component(HealthComponent.class).currentHealth();
    }

    public void setCurrentHealth(int currentHealth) {
        updateComponent(HealthComponent.class, current -> new HealthComponent(currentHealth, current.maxHealth()));
    }

    public int getMaxHealth() {
        return component(HealthComponent.class).maxHealth();
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

    public MovementSystem getMovementSystem() {
        return movementSystem;
    }

    public int rollInitiative() {
        return DiceRoller.roll(new DiceExpression(1, 20, getModifier(Attribute.DEXTERITY))).total();
    }

    public boolean onEnteredCell(HexCoordinate cell) {
        return false;
    }

    // No-op par défaut : seul GamePlayer a une Connection à notifier.
    public void send(OutputMessage message) {
    }
}
