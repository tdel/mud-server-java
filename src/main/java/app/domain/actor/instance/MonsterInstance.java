package app.domain.actor.instance;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import app.domain.SpellElement;
import app.domain.actor.Attribute;
import app.domain.actor.AbstractCharacter;
import app.domain.actor.template.MonsterTemplate;
import app.domain.actor.event.CharacterDied;
import app.domain.actor.event.DomainEventPublisher;
import app.domain.map.Position;
import app.domain.world.PeaceZone;
import app.game.combat.CombatFormulas;
import app.game.engine.MonsterAiEngine;
import app.game.dice.DiceRoller;

public final class MonsterInstance extends AbstractCharacter {

    private static final Logger log = LoggerFactory.getLogger(MonsterInstance.class);

    private final UUID templateId;
    private final UUID mapId;
    private final Position spawnPosition;

    private MonsterTemplate template;

    public volatile MonsterAiEngine.PursuitState pursuit;

    public MonsterInstance(UUID id, String name, UUID templateId, UUID mapId, Map<Attribute, Integer> attributes,
            int maxHealth, Position spawnPosition) {
        super(id, name, attributes, maxHealth, maxHealth);
        this.templateId = templateId;
        this.mapId = mapId;
        this.spawnPosition = spawnPosition;
    }

    public boolean takeDamage(int amount, CharacterInstance attacker) {
        boolean defeated;
        int healthAfter;
        synchronized (this) {
            if (getCurrentHealth() <= 0) {
                return false;
            }
            if (getZone() instanceof PeaceZone) {
                return false;
            }
            setCurrentHealth(Math.max(0, getCurrentHealth() - amount));
            defeated = getCurrentHealth() <= 0;
            healthAfter = getCurrentHealth();
        }
        log.debug("monster.take_damage thread={} monsterId={} attacker={} amount={} healthAfter={}",
                Thread.currentThread().getName(), getId(), attacker.getId(), amount, healthAfter);
        if (defeated) {
            DomainEventPublisher.publish(new CharacterDied(this, attacker));
        }
        return defeated;
    }

    public MonsterAttackOutcome attack(CharacterInstance defender) {
        double hitChance = CombatFormulas.hitChance(getEffectiveAccuracy(), defender.getEffectiveEvasion());
        boolean hit = DiceRoller.rollChance(hitChance);

        int damage = 0;
        boolean critical = false;
        boolean defeated = false;
        int healthAfter = defender.getCurrentHealth();

        if (hit) {
            critical = DiceRoller.rollChance(getEffectiveCriticalRate() / 100.0);
            damage = CombatFormulas.resolveDamage(getEffectivePAtk(), defender.getEffectivePDef(), critical);
            healthAfter = Math.max(0, defender.getCurrentHealth() - damage);
            defeated = defender.takeDamage(damage, this);
        }

        log.info(
                "combat.attack_resolved attacker={} defender={} hit={} critical={} damage={} defenderHealthAfter={} defeated={}",
                getId(), defender.getId(), hit, critical, damage, healthAfter, defeated);

        return new MonsterAttackOutcome(hit, critical, damage, healthAfter, defender.getMaxHealth(), defeated);
    }

    public record MonsterAttackOutcome(boolean hit, boolean critical, int damage, int targetHealthAfter,
            int targetMaxHealth, boolean targetDefeated) {
    }

    public void attachTemplate(MonsterTemplate template) {
        this.template = template;
    }

    public MonsterTemplate getTemplate() {
        return template;
    }

    public String getDescription() {
        return requireTemplate().getDescription();
    }

    public int getPresenceRadius() {
        return requireTemplate().getPresenceRadius();
    }

    public int getLevel() {
        return requireTemplate().getLevel();
    }

    @Override
    public int getSpeed() {
        return requireTemplate().getSpeed();
    }

    @Override
    protected int basePAtk() {
        return requireTemplate().getNaturalPAtk();
    }

    @Override
    protected int baseMAtk() {
        return requireTemplate().getNaturalMAtk();
    }

    @Override
    protected int basePDefSum() {
        return requireTemplate().getNaturalPDef();
    }

    @Override
    protected int baseMDefSum() {
        return requireTemplate().getNaturalMDef();
    }

    @Override
    protected int accuracyItemBonus() {
        return requireTemplate().getAccuracyBonus();
    }

    @Override
    protected int evasionItemBonus() {
        return requireTemplate().getEvasionBonus();
    }

    @Override
    protected int critItemBonus() {
        return requireTemplate().getCritBonus();
    }

    @Override
    protected int baseAtkSpd() {
        return requireTemplate().getAtkSpd();
    }

    @Override
    protected Map<SpellElement, Integer> elementalResistanceMap() {
        return requireTemplate().getElementalResistances();
    }

    private MonsterTemplate requireTemplate() {
        if (template == null) {
            throw new IllegalStateException("GameMonster " + getId() + " has no MonsterTemplate attached");
        }
        return template;
    }

    public UUID getTemplateId() {
        return templateId;
    }

    public Position getSpawnPosition() {
        return spawnPosition;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof MonsterInstance other)) {
            return false;
        }
        return getCurrentHealth() == other.getCurrentHealth() && getMaxHealth() == other.getMaxHealth()
                && Objects.equals(getId(), other.getId()) && Objects.equals(getName(), other.getName())
                && Objects.equals(templateId, other.templateId) && Objects.equals(mapId, other.mapId)
                && Objects.equals(spawnPosition, other.spawnPosition)
                && Objects.equals(getAttributes(), other.getAttributes());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId(), getName(), templateId, mapId, spawnPosition, getAttributes(), getCurrentHealth(),
                getMaxHealth());
    }

    @Override
    public String toString() {
        return "GameMonster[id=" + getId() + ", name=" + getName() + ", templateId=" + templateId + ", mapId=" + mapId
                + ", spawnPosition=" + spawnPosition + ", currentHealth=" + getCurrentHealth() + ", maxHealth="
                + getMaxHealth() + "]";
    }
}
