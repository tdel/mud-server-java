package fr.idev.mudserver.domain.actor.component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import fr.idev.mudserver.domain.Spell;
import fr.idev.mudserver.domain.actor.AbstractCharacter;
import fr.idev.mudserver.domain.actor.ModifiedStat;
import fr.idev.mudserver.domain.actor.instance.CharacterInstance;
import fr.idev.mudserver.domain.actor.instance.MonsterInstance;
import fr.idev.mudserver.game.dice.DiceRoll;
import fr.idev.mudserver.game.dice.DiceRoller;

public final class SpellCasting {

    private final CharacterInstance character;
    private final Set<UUID> knownSpellIds = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Instant> nextCastAt = new ConcurrentHashMap<>();

    public SpellCasting(CharacterInstance character) {
        this.character = character;
    }

    public boolean knows(UUID spellId) {
        return knownSpellIds.contains(spellId);
    }

    public boolean learn(UUID spellId) {
        return knownSpellIds.add(spellId);
    }

    public Set<UUID> knownSpellIds() {
        return Set.copyOf(knownSpellIds);
    }

    public boolean isReady(UUID spellId) {
        return !Instant.now().isBefore(nextCastAt.getOrDefault(spellId, Instant.MIN));
    }

    public Duration remainingCooldown(UUID spellId) {
        Duration remaining = Duration.between(Instant.now(), nextCastAt.getOrDefault(spellId, Instant.MIN));
        return remaining.isNegative() ? Duration.ZERO : remaining;
    }

    public CastOutcome cast(Spell spell, AbstractCharacter target) {
        CastOutcome outcome = switch (spell.effect()) {
            case HEALING -> castHeal(spell);
            case DAMAGE -> castDamage(spell, target);
            case BUFF -> castModifier(spell, target, false);
            case DEBUFF -> castModifier(spell, target, true);
        };

        nextCastAt.put(spell.id(), Instant.now().plusSeconds(spell.cooldownSeconds()));
        return outcome;
    }

    private CastOutcome castHeal(Spell spell) {
        int amount = character.heal(DiceRoller.roll(spell.effectDice()).total());
        return new CastOutcome(true, amount, character.getCurrentHealth(), character.getMaxHealth(), false, true, null);
    }

    private CastOutcome castDamage(Spell spell, AbstractCharacter target) {
        if (!rollSpellAttack(target)) {
            return new CastOutcome(false, 0, target.getCurrentHealth(), target.getMaxHealth(), false, false, null);
        }
        int amount = DiceRoller.roll(spell.effectDice()).total();
        boolean defeated = applyDamage(target, amount);
        return new CastOutcome(true, amount, target.getCurrentHealth(), target.getMaxHealth(), defeated, false, null);
    }

    private CastOutcome castModifier(Spell spell, AbstractCharacter target, boolean debuff) {
        if (debuff && !rollSpellAttack(target)) {
            return new CastOutcome(false, 0, target.getCurrentHealth(), target.getMaxHealth(), false, false, null);
        }

        int rolled = DiceRoller.roll(spell.effectDice()).total();
        int amount = debuff ? -rolled : rolled;
        Instant expiresAt = Instant.now().plusSeconds(spell.durationSeconds());
        target.getActiveEffects()
                .apply(new ActiveEffect(spell.id(), spell.name(), spell.modifiedStat(), amount, expiresAt));
        return new CastOutcome(true, amount, target.getCurrentHealth(), target.getMaxHealth(), false, false, expiresAt);
    }

    private boolean rollSpellAttack(AbstractCharacter target) {
        int spellAttackBonus = character.getSpellAttackBonus()
                + character.getActiveEffects().totalModifier(ModifiedStat.ATTACK_ROLL);
        DiceRoll attackRoll = DiceRoller.rollD20(spellAttackBonus, false);
        return DiceRoller.resolveHit(attackRoll.rolls()[0], attackRoll.total(), target.getEffectiveArmorClass());
    }

    private boolean applyDamage(AbstractCharacter defender, int damage) {
        if (defender instanceof CharacterInstance targetPlayer) {
            return targetPlayer.takeDamage(damage, character);
        }
        if (defender instanceof MonsterInstance targetMonster) {
            return targetMonster.takeDamage(damage, character);
        }
        throw new IllegalStateException("Cible de sort non supportée : " + defender.getClass());
    }

    public record CastOutcome(boolean hit, int amount, int targetHealthAfter, int targetMaxHealth,
            boolean targetDefeated, boolean selfHeal, Instant effectExpiresAt) {
    }
}
