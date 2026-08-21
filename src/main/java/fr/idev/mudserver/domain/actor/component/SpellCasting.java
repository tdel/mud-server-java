package fr.idev.mudserver.domain.actor.component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import fr.idev.mudserver.domain.Spell;
import fr.idev.mudserver.domain.SpellEffectType;
import fr.idev.mudserver.domain.actor.AbstractCharacter;
import fr.idev.mudserver.domain.actor.instance.CharacterInstance;
import fr.idev.mudserver.domain.actor.instance.MonsterInstance;
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
        boolean selfHeal = spell.effect() == SpellEffectType.HEALING;
        int amount = DiceRoller.roll(spell.effectDice()).total();
        boolean defeated = false;
        int healthAfter;
        int maxHealth;

        if (selfHeal) {
            amount = character.heal(amount);
            healthAfter = character.getCurrentHealth();
            maxHealth = character.getMaxHealth();
        } else {
            defeated = applyDamage(target, amount);
            healthAfter = target.getCurrentHealth();
            maxHealth = target.getMaxHealth();
        }

        nextCastAt.put(spell.id(), Instant.now().plusSeconds(spell.cooldownSeconds()));
        return new CastOutcome(amount, healthAfter, maxHealth, defeated, selfHeal);
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

    public record CastOutcome(int amount, int targetHealthAfter, int targetMaxHealth, boolean targetDefeated,
            boolean selfHeal) {
    }
}
