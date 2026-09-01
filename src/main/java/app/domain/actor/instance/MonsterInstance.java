package app.domain.actor.instance;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import app.domain.ActiveEffect;
import app.domain.ActiveSkill;
import app.domain.PassiveSkill;
import app.domain.SkillElement;
import app.domain.actor.Attribute;
import app.domain.actor.AbstractCharacter;
import app.domain.actor.ModifiedStat;
import app.domain.actor.template.MonsterTemplate;
import app.domain.actor.event.CharacterDied;
import app.domain.actor.event.DomainEventPublisher;
import app.domain.map.Position;
import app.domain.world.PeaceZone;
import app.game.combat.CombatFormulas;
import app.game.engine.MonsterAiEngine;
import app.game.Randomizer;

public final class MonsterInstance extends AbstractCharacter {

    private static final Logger log = LoggerFactory.getLogger(MonsterInstance.class);

    private final UUID templateId;
    private final UUID mapId;
    private final Position spawnPosition;

    private final MonsterTemplate template;

    public volatile MonsterAiEngine.PursuitState pursuit;

    public MonsterInstance(UUID id, String name, MonsterTemplate template, UUID mapId,
            Map<Attribute, Integer> attributes, int maxHealth, Position spawnPosition, Set<ActiveSkill> knownSkills,
            Set<PassiveSkill> knownPassiveSkills, List<ActiveEffect> activeEffects) {
        super(id, name, attributes, maxHealth, maxHealth, knownSkills, knownPassiveSkills, activeEffects,
                baseStats(template, attributes));
        this.template = Objects.requireNonNull(template);
        this.templateId = template.getId();
        this.mapId = mapId;
        this.spawnPosition = spawnPosition;
    }

    private static Map<ModifiedStat, Integer> baseStats(MonsterTemplate template, Map<Attribute, Integer> attributes) {
        Map<ModifiedStat, Integer> stats = CombatFormulas.baseStats(template.getPAtk(), template.getMAtk(),
                template.getPDef(), template.getMDef(), template.getAccuracyBonus(), template.getEvasionBonus(),
                template.getCritBonus(), 0, template.getAtkSpd(), attributes, template.getLevel());
        stats.put(ModifiedStat.SPEED, template.getSpeed());
        return stats;
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
        double hitChance = CombatFormulas.hitChance(getStatSystem().getEffective(ModifiedStat.ACCURACY),
                defender.getStatSystem().getEffective(ModifiedStat.EVASION));
        boolean hit = Randomizer.rollChance(hitChance);

        int damage = 0;
        boolean critical = false;
        boolean defeated = false;
        int healthAfter = defender.getCurrentHealth();

        if (hit) {
            critical = Randomizer.rollChance(getStatSystem().getEffective(ModifiedStat.PCRIT) / 100.0);
            damage = CombatFormulas.resolveDamage(getStatSystem().getEffective(ModifiedStat.PATK),
                    defender.getStatSystem().getEffective(ModifiedStat.PDEF), critical);
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

    public MonsterTemplate getTemplate() {
        return template;
    }

    public String getDescription() {
        return template.getDescription();
    }

    public int getPresenceRadius() {
        return template.getPresenceRadius();
    }

    public int getLevel() {
        return template.getLevel();
    }

    @Override
    protected Map<SkillElement, Integer> elementalResistanceMap() {
        return template.getElementalResistances();
    }

    public UUID getTemplateId() {
        return templateId;
    }

    public Position getSpawnPosition() {
        return spawnPosition;
    }

    @Override
    public String toString() {
        return "GameMonster[id=" + getId() + ", name=" + getName() + ", templateId=" + templateId + ", mapId=" + mapId
                + ", spawnPosition=" + spawnPosition + ", currentHealth=" + getCurrentHealth() + ", maxHealth="
                + getMaxHealth() + "]";
    }
}
