package fr.idev.mudserver.domain.actor;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

import fr.idev.mudserver.domain.actor.component.ActiveEffects;
import fr.idev.mudserver.domain.actor.component.SpellCasting;
import fr.idev.mudserver.domain.map.HexCoordinate;
import fr.idev.mudserver.domain.world.ZoneInstance;
import fr.idev.mudserver.game.engine.MovementEngine;
import fr.idev.mudserver.game.dice.DiceExpression;
import fr.idev.mudserver.game.dice.DiceRoller;
import fr.idev.mudserver.network.OutputMessage;

public abstract class AbstractCharacter extends AbstractObject {

    public static final int DEFAULT_SPEED = 6;

    private final Map<Attribute, Integer> attributes;
    private final ActiveEffects activeEffects = new ActiveEffects();
    private final SpellCasting spellCasting = new SpellCasting(this);
    private int currentHealth;
    private int maxHealth;

    private volatile ZoneInstance currentZone;
    private volatile HexCoordinate position;
    protected int speed = DEFAULT_SPEED;
    public volatile MovementEngine.ActiveMovement activeMovement;

    protected AbstractCharacter(UUID id, String name, Map<Attribute, Integer> attributes, int currentHealth,
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

    public final int getEffectiveArmorClass() {
        return getArmorClass() + activeEffects.totalModifier(ModifiedStat.ARMOR_CLASS);
    }

    public ActiveEffects getActiveEffects() {
        return activeEffects;
    }

    public SpellCasting getSpellCasting() {
        return spellCasting;
    }

    // Défaut neutre pour les sous-classes qui ne lancent pas encore de sorts
    // (MonsterInstance, AbstractNpc) ; CharacterInstance surcharge avec le calcul
    // DnD5e réel.
    public int getSpellAttackBonus() {
        return 0;
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

    public ZoneInstance getCurrentZone() {
        return currentZone;
    }

    public void setCurrentZone(ZoneInstance currentZone) {
        this.currentZone = currentZone;
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

    public int rollInitiative() {
        return DiceRoller.roll(new DiceExpression(1, 20, getModifier(Attribute.DEXTERITY))).total();
    }

    // No-op par défaut : seul GamePlayer a une Connection à notifier.
    public void send(OutputMessage message) {
    }

}
