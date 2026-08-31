package app.domain.actor.component;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import app.domain.actor.AbstractCharacter;
import app.domain.actor.AbstractNpc;
import app.domain.actor.event.AttackBegin;
import app.domain.actor.event.DomainEventPublisher;
import app.domain.actor.event.MonsterAttacked;
import app.domain.actor.instance.CharacterInstance;
import app.domain.actor.instance.MonsterInstance;
import app.domain.world.PeaceZone;
import app.game.combat.CombatFormulas;
import app.game.dice.DiceRoller;
import app.game.engine.MonsterAiEngine;
import app.network.message.ingame.AttackResult;

public final class CharacterCombat {

    private static final Logger log = LoggerFactory.getLogger(CharacterCombat.class);

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
        if (defender == null) {
            return new AttackOutcome.NoTarget();
        }
        if (defender.getCurrentHealth() <= 0) {
            log.debug("attack.rejected character={} reason=target_dead target={}", character.getId(), defender.getId());
            setTarget(null);
            return new AttackOutcome.TargetInvalid(defender.getId());
        }
        if (defender instanceof AbstractNpc) {
            log.debug("attack.rejected character={} reason=target_not_attackable target={}", character.getId(),
                    defender.getId());
            return new AttackOutcome.TargetInvalid(defender.getId());
        }
        PeaceZone peaceZone = character.getZone() instanceof PeaceZone attackerZone
                ? attackerZone
                : defender.getZone() instanceof PeaceZone defenderZone ? defenderZone : null;
        if (peaceZone != null) {
            log.debug("attack.rejected character={} reason=peace_zone zone={} target={}", character.getId(),
                    peaceZone.getName(), defender.getId());
            return new AttackOutcome.ForbiddenZone(peaceZone.getName());
        }
        if (character.getPosition().distanceTo(defender.getPosition()) > MonsterAiEngine.ATTACK_RANGE) {
            log.debug("attack.rejected character={} reason=out_of_range target={}", character.getId(),
                    defender.getId());
            return new AttackOutcome.OutOfRange(defender.getName());
        }
        if (!isReady()) {
            log.debug("attack.rejected character={} reason=on_cooldown target={}", character.getId(), defender.getId());
            return new AttackOutcome.OnCooldown(remainingCooldown().toMillis());
        }

        DomainEventPublisher.publish(new AttackBegin(character));

        if (defender instanceof MonsterInstance monster) {
            DomainEventPublisher.publish(new MonsterAttacked(monster, character));
        }

        double hitChance = CombatFormulas.hitChance(character.getEffectiveAccuracy(), defender.getEffectiveEvasion());
        boolean hit = DiceRoller.rollChance(hitChance);

        int damage = 0;
        boolean critical = false;
        boolean defeated = false;
        int healthAfter = defender.getCurrentHealth();

        if (hit) {
            critical = DiceRoller.rollChance(character.getEffectiveCriticalRate() / 100.0);
            damage = CombatFormulas.resolveDamage(character.getEffectivePAtk(), defender.getEffectivePDef(), critical);
            healthAfter = Math.max(0, defender.getCurrentHealth() - damage);
            defeated = applyDamage(defender, damage);
        }

        nextAttackAt = Instant.now().plus(CombatFormulas.attackCooldown(character.getEffectiveAtkSpd()));

        log.info(
                "combat.attack_resolved attacker={} defender={} hit={} critical={} damage={} defenderHealthAfter={} defeated={}",
                character.getId(), defender.getId(), hit, critical, damage, healthAfter, defeated);

        character.broadcast(new AttackResult(character.getId(), character.getName(), defender.getId(),
                defender.getName(), hit, critical, damage, healthAfter), null);
        return new AttackOutcome.Success();
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

    public sealed interface AttackOutcome {

        record Success() implements AttackOutcome {
        }

        record NoTarget() implements AttackOutcome {
        }

        record TargetInvalid(UUID targetId) implements AttackOutcome {
        }

        record ForbiddenZone(String zoneName) implements AttackOutcome {
        }

        record OutOfRange(String targetName) implements AttackOutcome {
        }

        record OnCooldown(long remainingMs) implements AttackOutcome {
        }
    }
}
