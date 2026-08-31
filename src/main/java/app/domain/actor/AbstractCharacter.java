package app.domain.actor;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import app.domain.ActiveSkill;
import app.domain.SkillEffectType;
import app.domain.SkillElement;
import app.domain.actor.system.EffectsSystem;
import app.domain.actor.system.SkillSystem;
import app.domain.actor.event.CharacterPositionChanged;
import app.domain.actor.event.DomainEventPublisher;
import app.domain.actor.instance.CharacterInstance;
import app.domain.actor.event.SkillCastBegin;
import app.domain.map.Position;
import app.domain.world.AbstractZone;
import app.domain.world.MapInstance;
import app.domain.world.NormalZone;
import app.game.combat.CombatFormulas;
import app.game.engine.MovementEngine;
import app.game.engine.SkillCastEngine;
import app.network.OutputMessage;

public abstract class AbstractCharacter extends AbstractObject {

    public static final int DEFAULT_SPEED = 110;

    private final Map<Attribute, Integer> attributes;
    private final EffectsSystem activeEffects = new EffectsSystem();
    private final SkillSystem skillSystem = new SkillSystem(this);
    private int currentHealth;
    private int maxHealth;

    private volatile MapInstance currentMap;
    private volatile Position position;
    private volatile double heading;
    protected int speed = DEFAULT_SPEED;
    private volatile MovementEngine.ActiveMovement activeMovement;
    private volatile SkillCastEngine.ActiveCast activeCast;
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

    // Défauts neutres : seul CharacterInstance a des objets équipés (arme,
    // armure) ; MonsterInstance surcharge depuis son MonsterTemplate.
    protected int basePAtk() {
        return CombatFormulas.UNARMED_PATK;
    }

    protected int baseMAtk() {
        return 0;
    }

    protected int basePDefSum() {
        return 0;
    }

    protected int baseMDefSum() {
        return 0;
    }

    protected int accuracyItemBonus() {
        return 0;
    }

    protected int evasionItemBonus() {
        return 0;
    }

    protected int armorWeightPenalty() {
        return 0;
    }

    protected int critItemBonus() {
        return 0;
    }

    protected int baseAtkSpd() {
        return CombatFormulas.BASE_ATK_SPD;
    }

    protected Map<SkillElement, Integer> elementalResistanceMap() {
        return Map.of();
    }

    public final int getElementalResistance(SkillElement element) {
        return elementalResistanceMap().getOrDefault(element, 0);
    }

    // Défaut neutre : seul CharacterInstance regroupe des items équipés
    // susceptibles de former un set.
    protected Map<ModifiedStat, Integer> setBonusModifiers() {
        return Map.of();
    }

    private int setBonus(ModifiedStat stat) {
        return setBonusModifiers().getOrDefault(stat, 0);
    }

    public int getPAtk() {
        return CombatFormulas.physicalAttack(basePAtk(), getAttribute(Attribute.STRENGTH), getLevel());
    }

    public final int getEffectivePAtk() {
        return getPAtk() + activeEffects.totalModifier(ModifiedStat.PATK) + setBonus(ModifiedStat.PATK);
    }

    public int getMAtk() {
        return CombatFormulas.magicalAttack(baseMAtk(), getAttribute(Attribute.INTELLIGENCE), getLevel());
    }

    public final int getEffectiveMAtk() {
        return getMAtk() + activeEffects.totalModifier(ModifiedStat.MATK) + setBonus(ModifiedStat.MATK);
    }

    public int getPDef() {
        return CombatFormulas.physicalDefense(basePDefSum(), getAttribute(Attribute.CONSTITUTION));
    }

    public final int getEffectivePDef() {
        return getPDef() + activeEffects.totalModifier(ModifiedStat.PDEF) + setBonus(ModifiedStat.PDEF);
    }

    public int getMDef() {
        return CombatFormulas.magicalDefense(baseMDefSum(), getAttribute(Attribute.MEN));
    }

    public final int getEffectiveMDef() {
        return getMDef() + activeEffects.totalModifier(ModifiedStat.MDEF) + setBonus(ModifiedStat.MDEF);
    }

    public int getAccuracy() {
        return CombatFormulas.accuracy(getLevel(), getAttribute(Attribute.DEXTERITY), accuracyItemBonus());
    }

    public final int getEffectiveAccuracy() {
        return getAccuracy() + activeEffects.totalModifier(ModifiedStat.ACCURACY) + setBonus(ModifiedStat.ACCURACY);
    }

    public int getEvasion() {
        return CombatFormulas.evasion(getLevel(), getAttribute(Attribute.DEXTERITY), armorWeightPenalty(),
                evasionItemBonus());
    }

    public final int getEffectiveEvasion() {
        return getEvasion() + activeEffects.totalModifier(ModifiedStat.EVASION) + setBonus(ModifiedStat.EVASION);
    }

    public int getCriticalRate() {
        return CombatFormulas.criticalRate(getAttribute(Attribute.DEXTERITY), critItemBonus());
    }

    public final int getEffectiveCriticalRate() {
        return getCriticalRate() + activeEffects.totalModifier(ModifiedStat.PCRIT);
    }

    public int getMagicalCriticalRate() {
        return CombatFormulas.magicCriticalRate(getAttribute(Attribute.WIT), critItemBonus());
    }

    public final int getEffectiveMagicalCriticalRate() {
        return getMagicalCriticalRate() + activeEffects.totalModifier(ModifiedStat.MCRIT);
    }

    public int getAtkSpd() {
        return CombatFormulas.attackSpeed(baseAtkSpd(), getAttribute(Attribute.DEXTERITY));
    }

    public final int getEffectiveAtkSpd() {
        return getAtkSpd() + activeEffects.totalModifier(ModifiedStat.ATKSPD) + setBonus(ModifiedStat.ATKSPD);
    }

    public EffectsSystem getActiveEffects() {
        return activeEffects;
    }

    public SkillSystem getSkillSystem() {
        return skillSystem;
    }

    // Défaut neutre : seul CharacterInstance a des objets équipés susceptibles
    // d'accorder des sorts.
    public Set<ActiveSkill> getGrantedSkills() {
        return Set.of();
    }

    public boolean hasSkill(ActiveSkill activeSkill) {
        return skillSystem.knows(activeSkill.id()) || getGrantedSkills().contains(activeSkill);
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
        AbstractZone newZone = currentMap != null && position != null
                ? currentMap.zoneAt(position)
                : NormalZone.INSTANCE;
        if (newZone != getZone()) {
            getZone().onObjectExiting(this);
            setZone(newZone);
            newZone.onObjectEntering(this);
        }
        if (position != null && this instanceof CharacterInstance character) {
            DomainEventPublisher.publish(new CharacterPositionChanged(character));
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

    public MovementEngine.ActiveMovement getActiveMovement() {
        return activeMovement;
    }

    public void updateMovement(MovementEngine.ActiveMovement movement) {
        this.activeMovement = movement;
    }

    public void clearMovement() {
        this.activeMovement = null;
    }

    public SkillCastEngine.ActiveCast getActiveCast() {
        return activeCast;
    }

    public void updateCast(SkillCastEngine.ActiveCast cast) {
        this.activeCast = cast;
    }

    public void clearCast() {
        this.activeCast = null;
    }

    public void setSpeed(int speed) {
        this.speed = speed;
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

    /**
     * Diffuse un message à TOUS les joueurs de la carte courante, sans passer par
     * la KnownList (portée de perception) — réservé aux événements de
     * présence/absence d'une entité (arrivée/départ d'un joueur, apparition/mort
     * d'un monstre) : la sélection d'une cible ne doit pas dépendre de la distance,
     * contrairement aux diffusions de mouvement/combat/chat qui, elles, restent
     * scopées à {@link #broadcast} pour la bande passante.
     */
    public void broadcastToMap(OutputMessage message, CharacterInstance exclude) {
        if (currentMap == null) {
            return;
        }
        for (CharacterInstance target : currentMap.characters()) {
            if (target != exclude) {
                target.send(message);
            }
        }
    }

    public CastRequestOutcome castSkill(ActiveSkill activeSkill, AbstractCharacter target) {
        if (!hasSkill(activeSkill)) {
            return new CastRequestOutcome.SkillUnknown(activeSkill.name());
        }
        if (target == null) {
            return new CastRequestOutcome.NoTarget();
        }
        if (target instanceof AbstractNpc && activeSkill.effect() == SkillEffectType.DAMAGE) {
            return new CastRequestOutcome.TargetInvalid(target.getId());
        }
        if (activeSkill.range() > 0 && getPosition().distanceTo(target.getPosition()) > activeSkill.range()) {
            return new CastRequestOutcome.OutOfRange(activeSkill.name(), target.getName());
        }
        if (!getSkillSystem().isReady(activeSkill.id())) {
            return new CastRequestOutcome.OnCooldown(activeSkill.name(),
                    getSkillSystem().remainingCooldown(activeSkill.id()).toMillis());
        }
        if (getCurrentMana() < activeSkill.manaCost()) {
            return new CastRequestOutcome.InsufficientMana(activeSkill.name(), activeSkill.manaCost(),
                    getCurrentMana());
        }

        DomainEventPublisher.publish(new SkillCastBegin(this, activeSkill, target));
        return new CastRequestOutcome.Started();
    }

    public sealed interface CastRequestOutcome {

        record Started() implements CastRequestOutcome {
        }

        record SkillUnknown(String skillName) implements CastRequestOutcome {
        }

        record NoTarget() implements CastRequestOutcome {
        }

        record TargetInvalid(UUID targetId) implements CastRequestOutcome {
        }

        record OutOfRange(String skillName, String targetName) implements CastRequestOutcome {
        }

        record OnCooldown(String skillName, long remainingMs) implements CastRequestOutcome {
        }

        record InsufficientMana(String skillName, int required, int current) implements CastRequestOutcome {
        }
    }

}
