package app.domain.actor.instance;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import app.domain.actor.AbstractCharacter;
import app.domain.actor.Attribute;
import app.domain.actor.CreatureRace;
import app.domain.actor.ModifiedStat;
import app.domain.actor.template.SummonTemplate;
import app.domain.actor.template.SummonTemplate.SummonLevel;
import app.game.combat.CombatFormulas;

// Serviteur invoqué par un joueur (Warlock, Necromancer, Dark Avenger...) : il
// suit son maître et combat sa cible (cf. game.engine.SummonEngine). Jamais
// persisté : il disparaît à la déconnexion, à sa mort ou à la fin de sa durée
// de vie. XP et butin de ses victimes reviennent à son maître.
public final class SummonInstance extends AbstractCharacter {

    private final PlayerInstance owner;
    private final SummonTemplate template;
    private final Instant expiresAt;
    // Cible de combat courante (null : suit son maître), pilotée par SummonEngine.
    private volatile AbstractCharacter combatTarget;
    private volatile boolean moving;
    private volatile long lastStepAtNanos = System.nanoTime();
    private volatile long nextSpecialAtMillis;

    public SummonInstance(PlayerInstance owner, SummonTemplate template, int skillLevel) {
        this(owner, template, template.levelFor(skillLevel));
    }

    private SummonInstance(PlayerInstance owner, SummonTemplate template, SummonLevel stats) {
        super(UUID.randomUUID(), template.name(), summonAttributes(), maxHealth(stats), maxHealth(stats), Map.of(),
                Map.of(), List.of(), baseStats(template, stats), false, 0, 0, List.of(), stats.level(), 0, 0, List.of(),
                null, null);
        this.owner = owner;
        this.template = template;
        this.expiresAt = Instant.now().plusSeconds(template.lifetimeSeconds());
        setTitle(owner.getName());
    }

    // Précision et critique de base des serviteurs L2 (accuracy 4.75, critical 4).
    private static final double NPC_ACCURACY = 4.75;
    private static final double NPC_CRIT_RATE = 4;

    // Attributs L2 des PNJ (STR 40, DEX 30, CON 43, INT 21, WIT 20, MEN 25).
    private static Map<Attribute, Integer> summonAttributes() {
        Map<Attribute, Integer> attributes = new EnumMap<>(Attribute.class);
        attributes.put(Attribute.STR, 40);
        attributes.put(Attribute.DEX, 30);
        attributes.put(Attribute.CON, 43);
        attributes.put(Attribute.INT, 21);
        attributes.put(Attribute.WIT, 20);
        attributes.put(Attribute.MEN, 25);
        return attributes;
    }

    // Valeurs de base du PNJ L2 passées par les fonctions de stats des
    // serviteurs (mêmes que celles des PNJ : PV x CON, p.atk x STR x niveau...).
    private static Map<ModifiedStat, Integer> baseStats(SummonTemplate template, SummonLevel stats) {
        return CombatFormulas.stats(
                new CombatFormulas.BaseValues(stats.pAtk(), stats.mAtk(), stats.pDef(), stats.mDef(), NPC_ACCURACY, 0,
                        NPC_CRIT_RATE, 0, template.atkSpd(), template.speed(), true, false),
                summonAttributes(), stats.level());
    }

    private static int maxHealth(SummonLevel stats) {
        return CombatFormulas.maxHealth(stats.hp(), summonAttributes().get(Attribute.CON));
    }

    public PlayerInstance getOwner() {
        return owner;
    }

    public SummonTemplate getTemplate() {
        return template;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    @Override
    public CreatureRace getCreatureRace() {
        return template.race();
    }

    public AbstractCharacter getCombatTarget() {
        return combatTarget;
    }

    public void setCombatTarget(AbstractCharacter combatTarget) {
        this.combatTarget = combatTarget;
    }

    @Override
    public void clearCombatTarget() {
        this.combatTarget = null;
        getCombatSystem().clearTarget();
    }

    public boolean isMoving() {
        return moving;
    }

    public void setMoving(boolean moving) {
        this.moving = moving;
    }

    public long getLastStepAtNanos() {
        return lastStepAtNanos;
    }

    public void setLastStepAtNanos(long lastStepAtNanos) {
        this.lastStepAtNanos = lastStepAtNanos;
    }

    public long getNextSpecialAtMillis() {
        return nextSpecialAtMillis;
    }

    public void setNextSpecialAtMillis(long nextSpecialAtMillis) {
        this.nextSpecialAtMillis = nextSpecialAtMillis;
    }

    @Override
    public String toString() {
        return "Summon[id=" + getId() + ", name=" + getName() + ", owner=" + owner.getName() + "]";
    }
}
