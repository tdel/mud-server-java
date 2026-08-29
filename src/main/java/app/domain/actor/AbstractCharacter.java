package app.domain.actor;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import app.domain.Spell;
import app.domain.SpellEffectType;
import app.domain.actor.component.ActiveEffects;
import app.domain.actor.component.SpellCasting;
import app.domain.actor.event.DomainEventPublisher;
import app.domain.actor.instance.CharacterInstance;
import app.domain.actor.event.SpellCastBegin;
import app.domain.map.Position;
import app.domain.world.AbstractZone;
import app.domain.world.MapInstance;
import app.domain.world.NormalZone;
import app.game.engine.MovementEngine;
import app.game.engine.SpellCastEngine;
import app.game.dice.DiceExpression;
import app.game.dice.DiceRoller;
import app.network.OutputMessage;
import app.network.message.ingame.NoTargetSelected;
import app.network.message.ingame.NotEnoughMana;
import app.network.message.ingame.SpellNotKnown;
import app.network.message.ingame.SpellOnCooldown;
import app.network.message.ingame.SpellOutOfRange;
import app.network.message.ingame.TargetNotFound;

public abstract class AbstractCharacter extends AbstractObject {

    public static final int DEFAULT_SPEED = 6;

    private final Map<Attribute, Integer> attributes;
    private final ActiveEffects activeEffects = new ActiveEffects();
    private final SpellCasting spellCasting = new SpellCasting(this);
    private int currentHealth;
    private int maxHealth;

    private volatile MapInstance currentMap;
    private volatile Position position;
    private volatile double heading;
    protected int speed = DEFAULT_SPEED;
    public volatile MovementEngine.ActiveMovement activeMovement;
    public volatile SpellCastEngine.ActiveCast activeCast;
    private final KnownList knownList = new KnownList(this);

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

    public abstract int getLevel();

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

    // Défaut neutre : seul CharacterInstance a des objets équipés susceptibles
    // d'accorder des sorts.
    public Set<Spell> getGrantedSpells() {
        return Set.of();
    }

    public boolean hasSpell(Spell spell) {
        return spellCasting.knows(spell.id()) || getGrantedSpells().contains(spell);
    }

    // Défaut neutre pour les sous-classes qui ne lancent pas encore de sorts
    // (MonsterInstance, AbstractNpc) ; CharacterInstance surcharge avec le calcul
    // DnD5e réel.
    public int getSpellAttackBonus() {
        return 0;
    }

    // Défaut neutre : seul CharacterInstance suit une réserve de mana ;
    // MonsterInstance/AbstractNpc n'en ont pas encore, donc jamais bloqués par le
    // coût en mana d'un sort.
    public int getCurrentMana() {
        return Integer.MAX_VALUE;
    }

    public int getMaxMana() {
        return Integer.MAX_VALUE;
    }

    public boolean trySpendMana(int amount) {
        return true;
    }

    // Défaut neutre : seul CharacterInstance a une CharacterCombat dont la cible
    // doit être effacée après un kill ; le ciblage d'un MonsterInstance (pursuit,
    // MonsterAiEngine) se recalcule de lui-même au prochain tick d'IA.
    public void clearCombatTarget() {
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

    public MapInstance getCurrentMap() {
        return currentMap;
    }

    public void setCurrentMap(MapInstance currentMap) {
        this.currentMap = currentMap;
    }

    public Position getPosition() {
        return position;
    }

    public void setPosition(Position position) {
        this.position = position;
        AbstractZone newZone = currentMap != null && position != null ? currentMap.zoneAt(position) : NormalZone.INSTANCE;
        if (newZone != getZone()) {
            getZone().onObjectExiting(this);
            setZone(newZone);
            newZone.onObjectEntering(this);
        }
    }

    public double getHeading() {
        return heading;
    }

    public void setHeading(double heading) {
        this.heading = heading;
    }

    public int getSpeed() {
        return speed;
    }

    public boolean isCasting() {
        return activeCast != null;
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

    public KnownList getKnownList() {
        return knownList;
    }

    /**
     * Diffuse un message à tous les personnages qui connaissent actuellement ce
     * personnage (sa KnownList), plus lui-même s'il s'agit d'un joueur (l'auteur
     * d'une action reçoit toujours sa propre diffusion, même s'il n'apparaît pas
     * dans sa propre KnownList).
     */
    public void broadcast(OutputMessage message, CharacterInstance exclude) {
        for (AbstractCharacter known : knownList.asList()) {
            if (known instanceof CharacterInstance target && target != exclude) {
                target.send(message);
            }
        }
        if (this instanceof CharacterInstance self && self != exclude) {
            self.send(message);
        }
    }

    public void castSpell(Spell spell, AbstractCharacter target) {
        if (!hasSpell(spell)) {
            send(new SpellNotKnown(spell.name()));
            return;
        }
        if (target == null) {
            send(new NoTargetSelected());
            return;
        }
        if (target instanceof AbstractNpc && spell.effect() == SpellEffectType.DAMAGE) {
            send(new TargetNotFound(target.getId().toString()));
            return;
        }
        if (spell.range() > 0 && getPosition().distanceTo(target.getPosition()) > spell.range()) {
            send(new SpellOutOfRange(spell.name(), target.getName()));
            return;
        }
        if (!getSpellCasting().isReady(spell.id())) {
            send(new SpellOnCooldown(spell.name(), getSpellCasting().remainingCooldown(spell.id()).toMillis()));
            return;
        }
        if (getCurrentMana() < spell.manaCost()) {
            send(new NotEnoughMana(spell.name(), spell.manaCost(), getCurrentMana()));
            return;
        }

        DomainEventPublisher.publish(new SpellCastBegin(this, spell, target));
    }

}
