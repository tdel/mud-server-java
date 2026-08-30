package app.domain.actor.component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import app.domain.Spell;
import app.domain.SpellElement;
import app.domain.actor.AbstractCharacter;
import app.domain.actor.Attribute;
import app.domain.actor.event.CharacterEffectExpired;
import app.domain.actor.event.DomainEventPublisher;
import app.domain.actor.event.MonsterAttacked;
import app.domain.actor.instance.CharacterInstance;
import app.domain.actor.instance.MonsterInstance;
import app.game.combat.CombatFormulas;
import app.game.dice.DiceRoller;

public final class SpellCasting {

    private final AbstractCharacter character;
    private final Set<Spell> knownSpells = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Instant> nextCastAt = new ConcurrentHashMap<>();

    public SpellCasting(AbstractCharacter character) {
        this.character = character;
    }

    public boolean knows(UUID spellId) {
        return knownSpells.stream().anyMatch(spell -> spell.id().equals(spellId));
    }

    public LearnResult learn(Spell spell) {
        Optional<Spell> existing = knownSpells.stream().filter(known -> known.name().equals(spell.name())).findFirst();
        if (existing.isEmpty()) {
            knownSpells.add(spell);
            return LearnResult.NEW;
        }
        if (existing.get().id().equals(spell.id())) {
            return LearnResult.ALREADY_KNOWN;
        }
        knownSpells.remove(existing.get());
        knownSpells.add(spell);
        return LearnResult.UPGRADED;
    }

    public enum LearnResult {
        NEW, UPGRADED, ALREADY_KNOWN
    }

    public Set<Spell> knownSpells() {
        return Set.copyOf(knownSpells);
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

        markCooldown(spell);
        return outcome;
    }

    public void markCooldown(Spell spell) {
        nextCastAt.put(spell.id(), Instant.now().plusSeconds(spell.cooldownSeconds()));
    }

    // Pour les sorts projectiles (cf. game.engine.ProjectileEngine) : le jet
    // d'attaque et les dégâts sont calculés à la fin de l'incantation, mais leur
    // application (PV décomptés, événement publié) est différée jusqu'à
    // l'impact.
    public AttackRollOutcome rollDamage(Spell spell, AbstractCharacter target) {
        if (!rollSpellHit(target)) {
            return new AttackRollOutcome(false, 0);
        }
        boolean critical = DiceRoller.rollChance(character.getEffectiveMagicalCriticalRate() / 100.0);
        double variance = DiceRoller.randomVariance(0.9, 1.1);
        // Le power du sort module la puissance magique du lancer, ce qui préserve
        // la progression entre tiers d'un même sort (Flame Strike tier 1 vs tier 5)
        // au lieu de tout aplatir sur le seul m.atk du personnage.
        int spellPower = character.getEffectiveMAtk() + spell.power();
        int amount = CombatFormulas.resolveDamage(spellPower, target.getEffectiveMDef(), variance, critical);
        if (spell.element() != SpellElement.NONE) {
            amount = CombatFormulas.applyElementalResistance(amount, target.getElementalResistance(spell.element()));
        }
        return new AttackRollOutcome(true, amount);
    }

    public CastOutcome applyDamageOutcome(AttackRollOutcome roll, AbstractCharacter target) {
        if (target instanceof MonsterInstance monster && character instanceof CharacterInstance casterPlayer) {
            DomainEventPublisher.publish(new MonsterAttacked(monster, casterPlayer));
        }
        if (!roll.hit()) {
            return new CastOutcome(false, 0, target.getCurrentHealth(), target.getMaxHealth(), false, false, null);
        }
        boolean defeated = applyDamage(target, roll.amount());
        return new CastOutcome(true, roll.amount(), target.getCurrentHealth(), target.getMaxHealth(), defeated, false,
                null);
    }

    private CastOutcome castHeal(Spell spell) {
        int amount = character.heal(spell.power());
        return new CastOutcome(true, amount, character.getCurrentHealth(), character.getMaxHealth(), false, true, null);
    }

    private CastOutcome castDamage(Spell spell, AbstractCharacter target) {
        return applyDamageOutcome(rollDamage(spell, target), target);
    }

    private CastOutcome castModifier(Spell spell, AbstractCharacter target, boolean debuff) {
        if (debuff && (!rollSpellHit(target)
                || DiceRoller.rollChance(CombatFormulas.debuffResistChance(target.getAttribute(Attribute.MEN))))) {
            return new CastOutcome(false, 0, target.getCurrentHealth(), target.getMaxHealth(), false, false, null);
        }

        int amount = debuff ? -spell.power() : spell.power();
        Instant expiresAt = Instant.now().plusSeconds(spell.durationSeconds());
        Optional<ActiveEffect> evicted = target.getActiveEffects()
                .apply(new ActiveEffect(spell.id(), spell.name(), spell.modifiedStat(), amount, expiresAt));
        evicted.ifPresent(effect -> DomainEventPublisher.publish(new CharacterEffectExpired(target, effect)));
        return new CastOutcome(true, amount, target.getCurrentHealth(), target.getMaxHealth(), false, false, expiresAt);
    }

    private boolean rollSpellHit(AbstractCharacter target) {
        double hitChance = CombatFormulas.hitChance(character.getEffectiveAccuracy(), target.getEffectiveEvasion());
        return DiceRoller.rollChance(hitChance);
    }

    private boolean applyDamage(AbstractCharacter defender, int damage) {
        if (defender instanceof CharacterInstance targetPlayer) {
            return targetPlayer.takeDamage(damage, character);
        }
        if (defender instanceof MonsterInstance targetMonster && character instanceof CharacterInstance casterPlayer) {
            return targetMonster.takeDamage(damage, casterPlayer);
        }
        throw new IllegalStateException("Cible de sort non supportée : " + defender.getClass());
    }

    public record CastOutcome(boolean hit, int amount, int targetHealthAfter, int targetMaxHealth,
            boolean targetDefeated, boolean selfHeal, Instant effectExpiresAt) {
    }

    public record AttackRollOutcome(boolean hit, int amount) {
    }
}
