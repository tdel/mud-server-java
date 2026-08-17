package fr.idev.mudserver.domain.actor.system;

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
        attacker.updateComponent(CombatComponent.class,
                current -> new CombatComponent(current.currentHealth(), current.maxHealth(), target,
                        current.actionsMax(), current.extraActionsMax(), current.actionsRemaining(),
                        current.extraActionsRemaining()));
    }

    public int heal(AbstractCharacter character, int amount) {
        int[] healed = {0};
        character.updateComponent(CombatComponent.class, current -> {
            healed[0] = Math.min(amount, current.maxHealth() - current.currentHealth());
            return new CombatComponent(current.currentHealth() + healed[0], current.maxHealth(), current.target(),
                    current.actionsMax(), current.extraActionsMax(), current.actionsRemaining(),
                    current.extraActionsRemaining());
        });
        return healed[0];
    }

    public void increaseMaxHealth(AbstractCharacter character, int amount) {
        character.updateComponent(CombatComponent.class,
                current -> new CombatComponent(current.currentHealth() + amount, current.maxHealth() + amount,
                        current.target(), current.actionsMax(), current.extraActionsMax(), current.actionsRemaining(),
                        current.extraActionsRemaining()));
    }

    public boolean trySpendAction(AbstractCharacter character) {
        boolean[] spent = {false};
        character.updateComponent(CombatComponent.class, current -> {
            if (current.actionsRemaining() > 0) {
                spent[0] = true;
                return new CombatComponent(current.currentHealth(), current.maxHealth(), current.target(),
                        current.actionsMax(), current.extraActionsMax(), current.actionsRemaining() - 1,
                        current.extraActionsRemaining());
            }
            if (current.extraActionsRemaining() > 0) {
                spent[0] = true;
                return new CombatComponent(current.currentHealth(), current.maxHealth(), current.target(),
                        current.actionsMax(), current.extraActionsMax(), current.actionsRemaining(),
                        current.extraActionsRemaining() - 1);
            }
            return current;
        });
        return spent[0];
    }

    public void resetForTurn(AbstractCharacter character) {
        character.updateComponent(CombatComponent.class,
                current -> new CombatComponent(current.currentHealth(), current.maxHealth(), current.target(),
                        current.actionsMax(), current.extraActionsMax(), current.actionsMax(),
                        current.extraActionsMax()));
    }

    public boolean hasActionRemaining(AbstractCharacter character) {
        CombatComponent current = character.component(CombatComponent.class);
        return current.actionsRemaining() > 0 || current.extraActionsRemaining() > 0;
    }

    public CombatResult tryAttack(AbstractCharacter attacker, AbstractCharacter target) {
        return switch (attacker) {
            case CharacterInstance player -> playerAttack(player, target);
            case MonsterInstance monster -> monsterAttack(monster, target);
            default -> throw new IllegalArgumentException("Unsupported attacker type: " + attacker.getClass());
        };
    }

    public boolean applyDamage(AbstractCharacter target, int amount, AbstractCharacter attacker) {
        boolean[] justDefeated = {false};
        target.updateComponent(CombatComponent.class, current -> {
            if (current.currentHealth() <= 0) {
                return current;
            }
            int newHealth = Math.max(0, current.currentHealth() - amount);
            if (newHealth <= 0) {
                justDefeated[0] = true;
            }
            return new CombatComponent(newHealth, current.maxHealth(), target, current.actionsMax(),
                    current.extraActionsMax(), current.actionsRemaining(), current.extraActionsRemaining());
        });

        if (justDefeated[0]) {
            publishDeath(target, attacker);
        }
        return justDefeated[0];
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
        boolean weaponProficient = weapon.map(item -> attacker.component(AppearanceComponent.class).characterClass()
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
            return new CombatResult(target.getName(), false, false, attackRoll.total(), armorClass, 0, disadvantage);
        }

        int damage = playerDamage(weapon, strengthModifier, criticalHit);
        return new CombatResult(target.getName(), true, criticalHit, attackRoll.total(), armorClass, damage,
                disadvantage);
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
            return new CombatResult(target.getName(), false, false, attackRoll.total(), armorClass, 0, false);
        }

        int damage = monsterDamage(attacker, strengthModifier, criticalHit);
        return new CombatResult(target.getName(), true, criticalHit, attackRoll.total(), armorClass, damage, false);
    }

    private int monsterDamage(MonsterInstance attacker, int strengthModifier, boolean criticalHit) {
        DiceExpression base = DiceExpression.parse(attacker.getNaturalDamageDice());
        int diceCount = criticalHit ? base.count() * 2 : base.count();
        return Math.max(0, DiceRoller.roll(new DiceExpression(diceCount, base.sides(), strengthModifier)).total());
    }
}
