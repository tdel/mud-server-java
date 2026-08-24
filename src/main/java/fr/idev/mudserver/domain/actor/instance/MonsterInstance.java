package fr.idev.mudserver.domain.actor.instance;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.IntStream;

import fr.idev.mudserver.domain.actor.Attribute;
import fr.idev.mudserver.domain.actor.AbstractCharacter;
import fr.idev.mudserver.domain.actor.ModifiedStat;
import fr.idev.mudserver.domain.actor.template.MonsterTemplate;
import fr.idev.mudserver.domain.actor.event.CharacterDied;
import fr.idev.mudserver.domain.actor.event.DomainEventPublisher;
import fr.idev.mudserver.domain.map.Position;
import fr.idev.mudserver.game.engine.MonsterAiEngine;
import fr.idev.mudserver.game.dice.DiceRoll;
import fr.idev.mudserver.game.dice.DiceRoller;

public final class MonsterInstance extends AbstractCharacter {

    private final UUID templateId;
    private final UUID zoneId;
    private final Position spawnPosition;

    private MonsterTemplate template;

    public volatile MonsterAiEngine.PursuitState pursuit;

    public MonsterInstance(UUID id, String name, UUID templateId, UUID zoneId, Map<Attribute, Integer> attributes,
            int maxHealth, Position spawnPosition) {
        super(id, name, attributes, maxHealth, maxHealth);
        this.templateId = templateId;
        this.zoneId = zoneId;
        this.spawnPosition = spawnPosition;
    }

    public boolean takeDamage(int amount, CharacterInstance attacker) {
        boolean defeated;
        synchronized (this) {
            if (getCurrentHealth() <= 0) {
                return false;
            }
            setCurrentHealth(Math.max(0, getCurrentHealth() - amount));
            defeated = getCurrentHealth() <= 0;
        }
        if (defeated) {
            DomainEventPublisher.publish(new CharacterDied(this, attacker));
        }
        return defeated;
    }

    public MonsterAttackOutcome attack(CharacterInstance defender) {
        int strengthModifier = getModifier(Attribute.STRENGTH);
        int attackModifier = strengthModifier + getActiveEffects().totalModifier(ModifiedStat.ATTACK_ROLL);
        DiceRoll attackRoll = DiceRoller.rollD20(attackModifier, false);
        int naturalRoll = attackRoll.rolls()[0];
        boolean critical = naturalRoll == 20;
        boolean hit = DiceRoller.resolveHit(naturalRoll, attackRoll.total(), defender.getEffectiveArmorClass());

        int damage = 0;
        boolean defeated = false;
        int healthAfter = defender.getCurrentHealth();

        if (hit) {
            int naturalDamage = rollNaturalDamage();
            if (critical) {
                naturalDamage += rollNaturalDamage();
            }
            damage = Math.max(0, naturalDamage + strengthModifier);
            healthAfter = Math.max(0, defender.getCurrentHealth() - damage);
            defeated = defender.takeDamage(damage, this);
        }

        return new MonsterAttackOutcome(hit, critical, damage, healthAfter, defender.getMaxHealth(), defeated);
    }

    private int rollNaturalDamage() {
        return IntStream.of(DiceRoller.roll(getNaturalDamageDice()).rolls()).sum();
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

    public String getNaturalDamageDice() {
        return requireTemplate().getNaturalDamageDice();
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
    public int getArmorClass() {
        Integer natural = requireTemplate().getNaturalArmorClass();
        return natural != null ? natural : super.getArmorClass();
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

    public UUID getZoneId() {
        return zoneId;
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
                && Objects.equals(templateId, other.templateId) && Objects.equals(zoneId, other.zoneId)
                && Objects.equals(spawnPosition, other.spawnPosition)
                && Objects.equals(getAttributes(), other.getAttributes());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId(), getName(), templateId, zoneId, spawnPosition, getAttributes(), getCurrentHealth(),
                getMaxHealth());
    }

    @Override
    public String toString() {
        return "GameMonster[id=" + getId() + ", name=" + getName() + ", templateId=" + templateId + ", zoneId=" + zoneId
                + ", spawnPosition=" + spawnPosition + ", currentHealth=" + getCurrentHealth() + ", maxHealth="
                + getMaxHealth() + "]";
    }
}
