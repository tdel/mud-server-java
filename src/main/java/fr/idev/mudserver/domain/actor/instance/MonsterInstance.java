package fr.idev.mudserver.domain.actor.instance;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import fr.idev.mudserver.domain.actor.Attribute;
import fr.idev.mudserver.domain.actor.AbstractCharacter;
import fr.idev.mudserver.domain.actor.template.MonsterTemplate;
import fr.idev.mudserver.domain.actor.event.CharacterDied;
import fr.idev.mudserver.domain.actor.event.DomainEventPublisher;
import fr.idev.mudserver.game.CombatResult;
import fr.idev.mudserver.game.dice.DiceExpression;
import fr.idev.mudserver.game.dice.DiceRoll;
import fr.idev.mudserver.game.dice.DiceRoller;

public final class MonsterInstance extends AbstractCharacter {

    private final UUID templateId;
    private final UUID roomId;

    private MonsterTemplate template;

    public MonsterInstance(UUID id, String name, UUID templateId, UUID roomId, Map<Attribute, Integer> attributes,
            int maxHealth) {
        super(id, name, attributes, maxHealth, maxHealth);
        this.templateId = templateId;
        this.roomId = roomId;
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

    public CombatResult tryAttack(CharacterInstance target) {
        int strengthModifier = getModifier(Attribute.STRENGTH);
        int attackBonus = strengthModifier + 2;

        DiceRoll attackRoll = DiceRoller.roll(new DiceExpression(1, 20, attackBonus));
        int naturalRoll = attackRoll.rolls()[0];
        boolean criticalHit = naturalRoll == 20;
        int armorClass = target.getArmorClass();
        boolean hit = DiceRoller.resolveHit(naturalRoll, attackRoll.total(), armorClass);

        if (!hit) {
            return new CombatResult(target.getName(), false, false, attackRoll.total(), armorClass, 0, false);
        }

        int damage = rollDamage(strengthModifier, criticalHit);
        return new CombatResult(target.getName(), true, criticalHit, attackRoll.total(), armorClass, damage, false);
    }

    private int rollDamage(int strengthModifier, boolean criticalHit) {
        DiceExpression base = DiceExpression.parse(getNaturalDamageDice());
        int diceCount = criticalHit ? base.count() * 2 : base.count();
        return Math.max(0, DiceRoller.roll(new DiceExpression(diceCount, base.sides(), strengthModifier)).total());
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

    public UUID getRoomId() {
        return roomId;
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
                && Objects.equals(templateId, other.templateId) && Objects.equals(roomId, other.roomId)
                && Objects.equals(getAttributes(), other.getAttributes());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId(), getName(), templateId, roomId, getAttributes(), getCurrentHealth(),
                getMaxHealth());
    }

    @Override
    public String toString() {
        return "GameMonster[id=" + getId() + ", name=" + getName() + ", templateId=" + templateId + ", roomId=" + roomId
                + ", currentHealth=" + getCurrentHealth() + ", maxHealth=" + getMaxHealth() + "]";
    }
}
