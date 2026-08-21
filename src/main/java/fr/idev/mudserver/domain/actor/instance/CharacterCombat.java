package fr.idev.mudserver.domain.actor.instance;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.stream.IntStream;

import fr.idev.mudserver.domain.actor.AbstractCharacter;
import fr.idev.mudserver.domain.actor.Attribute;
import fr.idev.mudserver.domain.item.EquipmentSlot;
import fr.idev.mudserver.domain.item.Item;
import fr.idev.mudserver.game.dice.DiceRoll;
import fr.idev.mudserver.game.dice.DiceRoller;

public final class CharacterCombat {

    public static final Duration ATTACK_COOLDOWN = Duration.ofSeconds(2);

    private final CharacterInstance character;
    private volatile AbstractCharacter target;
    private volatile Instant nextAttackAt = Instant.MIN;

    public CharacterCombat(CharacterInstance character) {
        this.character = character;
    }

    public AbstractCharacter getTarget() {
        return target;
    }

    public void setTarget(AbstractCharacter target) {
        this.target = target;
    }

    public boolean isReady() {
        return !Instant.now().isBefore(nextAttackAt);
    }

    public Duration remainingCooldown() {
        Duration remaining = Duration.between(Instant.now(), nextAttackAt);
        return remaining.isNegative() ? Duration.ZERO : remaining;
    }

    public AttackOutcome attack(AbstractCharacter defender) {
        int strengthModifier = character.getModifier(Attribute.STRENGTH);
        Optional<Item> weapon = getEquippedWeapon();
        boolean proficient = weapon.map(item -> character.getWeaponProficiencies().contains(item.getWeaponCategory()))
                .orElse(true);
        int attackModifier = strengthModifier + (proficient ? character.getProficiencyBonus() : 0);

        DiceRoll attackRoll = DiceRoller.rollD20(attackModifier, false);
        int naturalRoll = attackRoll.rolls()[0];
        boolean critical = naturalRoll == 20;
        boolean hit = DiceRoller.resolveHit(naturalRoll, attackRoll.total(), defender.getArmorClass());

        int damage = 0;
        boolean defeated = false;
        int healthAfter = defender.getCurrentHealth();

        if (hit) {
            int weaponDamage = rollWeaponDamage(weapon);
            if (critical) {
                weaponDamage += rollWeaponDamage(weapon);
            }
            damage = Math.max(0, weaponDamage + strengthModifier);
            healthAfter = Math.max(0, defender.getCurrentHealth() - damage);
            defeated = applyDamage(defender, damage);
        }

        nextAttackAt = Instant.now().plus(ATTACK_COOLDOWN);
        return new AttackOutcome(hit, critical, damage, healthAfter, defender.getMaxHealth(), defeated);
    }

    private int rollWeaponDamage(Optional<Item> weapon) {
        return weapon.map(item -> IntStream.of(DiceRoller.roll(item.getDamageDice()).rolls()).sum()).orElse(1);
    }

    private boolean applyDamage(AbstractCharacter defender, int damage) {
        if (defender instanceof CharacterInstance targetPlayer) {
            return targetPlayer.takeDamage(damage, character);
        }
        if (defender instanceof MonsterInstance targetMonster) {
            return targetMonster.takeDamage(damage, character);
        }
        throw new IllegalStateException("Cible de combat non supportée : " + defender.getClass());
    }

    private Optional<Item> getEquippedWeapon() {
        return character.getInventory().getEquippedItems().stream()
                .filter(item -> item.getSlot() == EquipmentSlot.WEAPON).findFirst();
    }

    public record AttackOutcome(boolean hit, boolean critical, int damage, int targetHealthAfter, int targetMaxHealth,
            boolean targetDefeated) {
    }
}
