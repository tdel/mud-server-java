package fr.idev.mudserver.domain.actor.system;

import fr.idev.mudserver.domain.actor.component.IdentityComponent;

import java.util.Optional;

import fr.idev.mudserver.domain.actor.component.LevelingComponent;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import fr.idev.mudserver.domain.actor.AbstractCharacter;
import fr.idev.mudserver.domain.actor.Attribute;
import fr.idev.mudserver.domain.actor.event.CharacterDied;
import fr.idev.mudserver.domain.actor.event.DomainEventPublisher;
import fr.idev.mudserver.domain.actor.event.GamePlayerDied;
import fr.idev.mudserver.domain.actor.instance.CharacterInstance;
import fr.idev.mudserver.domain.actor.instance.MonsterInstance;
import fr.idev.mudserver.domain.actor.component.AppearanceComponent;
import fr.idev.mudserver.domain.actor.component.AttributeComponent;
import fr.idev.mudserver.domain.actor.component.CombatComponent;
import fr.idev.mudserver.domain.actor.component.HealthComponent;
import fr.idev.mudserver.domain.actor.component.MonsterCombatComponent;
import fr.idev.mudserver.domain.item.Item;
import fr.idev.mudserver.game.CombatResult;
import fr.idev.mudserver.game.dice.DiceExpression;
import fr.idev.mudserver.game.dice.DiceRoll;
import fr.idev.mudserver.game.dice.DiceRoller;

@Service
public class CombatSystem {

    private final InventorySystem inventorySystem;
    private final LevelingSystem levelingSystem;

    public CombatSystem(InventorySystem inventorySystem, @Lazy LevelingSystem levelingSystem) {
        this.inventorySystem = inventorySystem;
        this.levelingSystem = levelingSystem;
    }

    public void setTarget(AbstractCharacter target, AbstractCharacter attacker) {
        synchronized (attacker) {
            attacker.component(CombatComponent.class).target = target;
        }
    }

    public int heal(AbstractCharacter character, int amount) {
        int healed;
        synchronized (character) {
            HealthComponent health = character.component(HealthComponent.class);
            healed = Math.min(amount, health.maxHealth - health.currentHealth);
            health.currentHealth += healed;
        }
        return healed;
    }

    public void increaseMaxHealth(AbstractCharacter character, int amount) {
        synchronized (character) {
            HealthComponent health = character.component(HealthComponent.class);
            health.currentHealth += amount;
            health.maxHealth += amount;
        }
    }

    public boolean trySpendAction(AbstractCharacter character) {
        synchronized (character) {
            CombatComponent combat = character.component(CombatComponent.class);
            if (combat.actionsRemaining > 0) {
                combat.actionsRemaining -= 1;
                return true;
            }
            if (combat.extraActionsRemaining > 0) {
                combat.extraActionsRemaining -= 1;
                return true;
            }
            return false;
        }
    }

    public void resetForTurn(AbstractCharacter character) {
        synchronized (character) {
            CombatComponent combat = character.component(CombatComponent.class);
            combat.actionsRemaining = combat.actionsMax;
            combat.extraActionsRemaining = combat.extraActionsMax;
        }
    }

    public boolean hasActionRemaining(AbstractCharacter character) {
        CombatComponent current = character.component(CombatComponent.class);
        return current.actionsRemaining > 0 || current.extraActionsRemaining > 0;
    }

    public CombatResult tryAttack(AbstractCharacter attacker, AbstractCharacter target) {
        return switch (attacker) {
            case CharacterInstance player -> playerAttack(player, target);
            case MonsterInstance monster -> monsterAttack(monster, target);
            default -> throw new IllegalArgumentException("Unsupported attacker type: " + attacker.getClass());
        };
    }

    public boolean applyDamage(AbstractCharacter target, int amount, AbstractCharacter attacker) {
        boolean justDefeated;
        synchronized (target) {
            HealthComponent health = target.component(HealthComponent.class);
            if (health.currentHealth <= 0) {
                return false;
            }
            health.currentHealth = Math.max(0, health.currentHealth - amount);
            justDefeated = health.currentHealth <= 0;
        }

        if (justDefeated) {
            publishDeath(target, attacker);
        }
        return justDefeated;
    }

    private void publishDeath(AbstractCharacter target, AbstractCharacter attacker) {
        switch (target) {
            case CharacterInstance player ->
                DomainEventPublisher.publish(new GamePlayerDied(player, (MonsterInstance) attacker));
            case MonsterInstance monster ->
                DomainEventPublisher.publish(new CharacterDied(monster, (CharacterInstance) attacker));
            default -> throw new IllegalArgumentException("Unsupported target type: " + target.getClass());
        }
    }

    private CombatResult playerAttack(CharacterInstance attacker, AbstractCharacter target) {
        Optional<Item> weapon = inventorySystem.equippedWeapon(attacker);
        int weaponBonus = weapon.map(Item::getBonus).orElse(0);
        boolean weaponProficient = weapon.map(item -> attacker.component(AppearanceComponent.class).characterClass
                .weaponProficiencies().contains(item.getWeaponCategory())).orElse(true);

        int strengthModifier = attacker.component(AttributeComponent.class).modifier(Attribute.STRENGTH);
        int attackBonus = strengthModifier
                + (weaponProficient ? attacker.component(LevelingComponent.class).proficiencyBonus() : 0) + weaponBonus;
        boolean disadvantage = inventorySystem.isWearingNonProficientArmor(attacker);

        DiceRoll attackRoll = DiceRoller.rollD20(attackBonus, disadvantage);
        int naturalRoll = attackRoll.rolls()[0];
        boolean criticalHit = naturalRoll == 20;
        int armorClass = inventorySystem.getArmorClass(target);
        boolean hit = DiceRoller.resolveHit(naturalRoll, attackRoll.total(), armorClass);

        if (!hit) {
            return new CombatResult(target.component(IdentityComponent.class).name, false, false, attackRoll.total(),
                    armorClass, 0, disadvantage);
        }

        int damage = playerDamage(weapon, strengthModifier, criticalHit);
        return new CombatResult(target.component(IdentityComponent.class).name, true, criticalHit, attackRoll.total(),
                armorClass, damage, disadvantage);
    }

    private int playerDamage(Optional<Item> weapon, int strengthModifier, boolean criticalHit) {
        if (weapon.isEmpty()) {
            // Attaque à mains nues (SRD) : 1 + modificateur de FOR, pas de dé donc rien à
            // doubler en cas de critique.
            return Math.max(0, 1 + strengthModifier);
        }

        DiceExpression base = DiceExpression.parse(weapon.get().getDamageDice());
        int diceCount = criticalHit ? base.count() * 2 : base.count();
        int modifier = strengthModifier + weapon.get().getBonus();
        return Math.max(0, DiceRoller.roll(new DiceExpression(diceCount, base.sides(), modifier)).total());
    }

    private CombatResult monsterAttack(MonsterInstance attacker, AbstractCharacter target) {
        int strengthModifier = attacker.component(AttributeComponent.class).modifier(Attribute.STRENGTH);
        int attackBonus = strengthModifier + 2;

        DiceRoll attackRoll = DiceRoller.roll(new DiceExpression(1, 20, attackBonus));
        int naturalRoll = attackRoll.rolls()[0];
        boolean criticalHit = naturalRoll == 20;
        int armorClass = inventorySystem.getArmorClass(target);
        boolean hit = DiceRoller.resolveHit(naturalRoll, attackRoll.total(), armorClass);

        if (!hit) {
            return new CombatResult(target.component(IdentityComponent.class).name, false, false, attackRoll.total(),
                    armorClass, 0, false);
        }

        int damage = monsterDamage(attacker, strengthModifier, criticalHit);
        return new CombatResult(target.component(IdentityComponent.class).name, true, criticalHit, attackRoll.total(),
                armorClass, damage, false);
    }

    private int monsterDamage(MonsterInstance attacker, int strengthModifier, boolean criticalHit) {
        DiceExpression base = DiceExpression.parse(attacker.component(MonsterCombatComponent.class).naturalDamageDice);
        int diceCount = criticalHit ? base.count() * 2 : base.count();
        return Math.max(0, DiceRoller.roll(new DiceExpression(diceCount, base.sides(), strengthModifier)).total());
    }
}
