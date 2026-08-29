package app.domain.actor.component;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.stream.IntStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import app.domain.actor.AbstractCharacter;
import app.domain.actor.AbstractNpc;
import app.domain.actor.Attribute;
import app.domain.actor.ModifiedStat;
import app.domain.actor.event.AttackBegin;
import app.domain.actor.event.DomainEventPublisher;
import app.domain.actor.event.MonsterAttacked;
import app.domain.actor.instance.CharacterInstance;
import app.domain.actor.instance.MonsterInstance;
import app.domain.item.EquipmentSlot;
import app.domain.item.Item;
import app.game.dice.DiceRoll;
import app.game.dice.DiceRoller;
import app.game.engine.MonsterAiEngine;
import app.network.message.ingame.AlreadyCasting;
import app.network.message.ingame.AttackOnCooldown;
import app.network.message.ingame.AttackOutOfRange;
import app.network.message.ingame.AttackResult;
import app.network.message.ingame.NoTargetSelected;
import app.network.message.ingame.TargetNotFound;

public final class CharacterCombat {

    private static final Logger log = LoggerFactory.getLogger(CharacterCombat.class);

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

    public void attack(AbstractCharacter defender) {
        if (character.isCasting()) {
            character.send(new AlreadyCasting());
            return;
        }
        if (defender == null) {
            character.send(new NoTargetSelected());
            return;
        }
        if (defender.getCurrentHealth() <= 0) {
            log.debug("attack.rejected character={} reason=target_dead target={}", character.getId(), defender.getId());
            setTarget(null);
            character.send(new TargetNotFound(defender.getId().toString()));
            return;
        }
        if (defender instanceof AbstractNpc) {
            log.debug("attack.rejected character={} reason=target_not_attackable target={}", character.getId(),
                    defender.getId());
            character.send(new TargetNotFound(defender.getId().toString()));
            return;
        }
        if (character.getPosition().distanceTo(defender.getPosition()) > MonsterAiEngine.ATTACK_RANGE) {
            log.debug("attack.rejected character={} reason=out_of_range target={}", character.getId(),
                    defender.getId());
            character.send(new AttackOutOfRange(defender.getName()));
            return;
        }
        if (!isReady()) {
            log.debug("attack.rejected character={} reason=on_cooldown target={}", character.getId(), defender.getId());
            character.send(new AttackOnCooldown(remainingCooldown().toMillis()));
            return;
        }

        DomainEventPublisher.publish(new AttackBegin(character));

        if (defender instanceof MonsterInstance monster) {
            DomainEventPublisher.publish(new MonsterAttacked(monster, character));
        }

        int strengthModifier = character.getModifier(Attribute.STRENGTH);
        Optional<Item> weapon = getEquippedWeapon();
        boolean proficient = weapon.map(item -> character.getWeaponProficiencies().contains(item.getWeaponCategory()))
                .orElse(true);
        int attackModifier = strengthModifier + (proficient ? character.getProficiencyBonus() : 0)
                + character.getActiveEffects().totalModifier(ModifiedStat.ATTACK_ROLL);

        DiceRoll attackRoll = DiceRoller.rollD20(attackModifier, false);
        int naturalRoll = attackRoll.rolls()[0];
        boolean critical = naturalRoll == 20;
        boolean hit = DiceRoller.resolveHit(naturalRoll, attackRoll.total(), defender.getEffectiveArmorClass());

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

        log.info(
                "combat.attack_resolved attacker={} defender={} hit={} critical={} damage={} defenderHealthAfter={} defeated={}",
                character.getId(), defender.getId(), hit, critical, damage, healthAfter, defeated);

        character.broadcast(new AttackResult(character.getId(), character.getName(), defender.getId(),
                defender.getName(), hit, critical, damage, healthAfter), null);
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
}
