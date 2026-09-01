package app.domain.actor.system;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import app.domain.ActiveEffect;
import app.domain.PassiveSkill;
import app.domain.ActiveSkill;
import app.domain.SkillEffectType;
import app.domain.SkillElement;
import app.domain.actor.AbstractCharacter;
import app.domain.actor.AbstractNpc;
import app.domain.actor.Attribute;
import app.domain.actor.ModifiedStat;
import app.domain.actor.event.CharacterBeginAttack;
import app.domain.actor.event.CharacterEffectExpired;
import app.domain.actor.event.DomainEventPublisher;
import app.domain.actor.event.SkillCastBegin;
import app.domain.actor.instance.CharacterInstance;
import app.domain.item.ItemGrade;
import app.game.Randomizer;
import app.game.combat.CombatFormulas;
import app.game.engine.SkillCastEngine;

public final class SkillSystem {

    private final AbstractCharacter character;
    private final Set<ActiveSkill> knownSkills = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Instant> nextCastAt = new ConcurrentHashMap<>();
    private final Set<PassiveSkill> knownPassiveSkills = ConcurrentHashMap.newKeySet();
    private volatile SkillCastEngine.ActiveCast activeCast;

    public SkillSystem(AbstractCharacter character) {
        this.character = character;
    }

    public boolean isCasting() {
        return activeCast != null;
    }

    public SkillCastEngine.ActiveCast getActiveCast() {
        return activeCast;
    }

    public void updateCast(SkillCastEngine.ActiveCast cast) {
        this.activeCast = cast;
    }

    public void clearCast() {
        this.activeCast = null;
    }

    public boolean knows(UUID skillId) {
        return knownSkills.stream().anyMatch(activeSkill -> activeSkill.id().equals(skillId));
    }

    public LearnResult learn(ActiveSkill activeSkill) {
        Optional<ActiveSkill> existing = knownSkills.stream().filter(known -> known.name().equals(activeSkill.name()))
                .findFirst();
        if (existing.isEmpty()) {
            knownSkills.add(activeSkill);
            return LearnResult.NEW;
        }
        if (existing.get().id().equals(activeSkill.id())) {
            return LearnResult.ALREADY_KNOWN;
        }
        knownSkills.remove(existing.get());
        knownSkills.add(activeSkill);
        return LearnResult.UPGRADED;
    }

    public enum LearnResult {
        NEW, UPGRADED, ALREADY_KNOWN
    }

    public Set<ActiveSkill> knownSkills() {
        return Set.copyOf(knownSkills);
    }

    // Seul CharacterInstance a des objets équipés susceptibles d'accorder des
    // sorts ; MonsterInstance/AbstractNpc n'en accordent jamais.
    public Set<ActiveSkill> getGrantedSkills() {
        if (!(character instanceof CharacterInstance player)) {
            return Set.of();
        }
        return player.getInventorySystem().getEquippedItems().stream().flatMap(item -> item.getGrantedSkills().stream())
                .collect(Collectors.toSet());
    }

    public boolean hasSkill(ActiveSkill activeSkill) {
        return knows(activeSkill.id()) || getGrantedSkills().contains(activeSkill);
    }

    public boolean learn(PassiveSkill passiveSkill) {
        return knownPassiveSkills.add(passiveSkill);
    }

    public Set<PassiveSkill> knownPassiveSkills() {
        return Set.copyOf(knownPassiveSkills);
    }

    public ItemGrade unlockedGrade() {
        return knownPassiveSkills.stream().map(PassiveSkill::grantsGrade).max(Comparator.comparingInt(Enum::ordinal))
                .orElse(ItemGrade.NOGRADE);
    }

    public boolean isReady(UUID skillId) {
        return !Instant.now().isBefore(nextCastAt.getOrDefault(skillId, Instant.MIN));
    }

    public Duration remainingCooldown(UUID skillId) {
        Duration remaining = Duration.between(Instant.now(), nextCastAt.getOrDefault(skillId, Instant.MIN));
        return remaining.isNegative() ? Duration.ZERO : remaining;
    }

    public CastOutcome cast(ActiveSkill activeSkill, AbstractCharacter target) {
        CastOutcome outcome = switch (activeSkill.effect()) {
            case HEALING -> castHeal(activeSkill);
            case DAMAGE -> castDamage(activeSkill, target);
            case BUFF -> castModifier(activeSkill, target, false);
            case DEBUFF -> castModifier(activeSkill, target, true);
        };

        markCooldown(activeSkill);
        return outcome;
    }

    public void markCooldown(ActiveSkill activeSkill) {
        nextCastAt.put(activeSkill.id(), Instant.now().plusSeconds(activeSkill.cooldownSeconds()));
    }

    // Pour les sorts projectiles (cf. game.engine.ProjectileEngine) : le jet
    // d'attaque et les dégâts sont calculés à la fin de l'incantation, mais leur
    // application (PV décomptés, événement publié) est différée jusqu'à
    // l'impact.
    public AttackRollOutcome rollDamage(ActiveSkill activeSkill, AbstractCharacter target) {
        if (!rollSkillHit(target)) {
            return new AttackRollOutcome(false, 0);
        }
        boolean critical = Randomizer.rollChance(character.getStatSystem().getEffective(ModifiedStat.MCRIT) / 100.0);
        // Le power du sort module la puissance magique du lancer, ce qui préserve
        // la progression entre tiers d'un même sort (Flame Strike tier 1 vs tier 5)
        // au lieu de tout aplatir sur le seul m.atk du personnage.
        int skillPower = character.getStatSystem().getEffective(ModifiedStat.MATK) + activeSkill.power();
        int amount = CombatFormulas.resolveDamage(skillPower, target.getStatSystem().getEffective(ModifiedStat.MDEF),
                critical);
        if (activeSkill.element() != SkillElement.NONE) {
            amount = CombatFormulas.applyElementalResistance(amount,
                    target.getElementalResistance(activeSkill.element()));
        }
        return new AttackRollOutcome(true, amount);
    }

    public CastOutcome applyDamageOutcome(AttackRollOutcome roll, AbstractCharacter target) {
        DomainEventPublisher.publish(new CharacterBeginAttack(character, target));
        if (!roll.hit()) {
            return new CastOutcome(false, 0, target.getCurrentHealth(), target.getMaxHealth(), false, false, null);
        }
        boolean defeated = applyDamage(target, roll.amount());
        return new CastOutcome(true, roll.amount(), target.getCurrentHealth(), target.getMaxHealth(), defeated, false,
                null);
    }

    private CastOutcome castHeal(ActiveSkill activeSkill) {
        int amount = character.heal(activeSkill.power());
        return new CastOutcome(true, amount, character.getCurrentHealth(), character.getMaxHealth(), false, true, null);
    }

    private CastOutcome castDamage(ActiveSkill activeSkill, AbstractCharacter target) {
        return applyDamageOutcome(rollDamage(activeSkill, target), target);
    }

    private CastOutcome castModifier(ActiveSkill activeSkill, AbstractCharacter target, boolean debuff) {
        if (debuff && (!rollSkillHit(target)
                || Randomizer.rollChance(CombatFormulas.debuffResistChance(target.getAttribute(Attribute.MEN))))) {
            return new CastOutcome(false, 0, target.getCurrentHealth(), target.getMaxHealth(), false, false, null);
        }

        int amount = debuff ? -activeSkill.power() : activeSkill.power();
        Instant expiresAt = Instant.now().plusSeconds(activeSkill.durationSeconds());
        Optional<ActiveEffect> evicted = target.getEffectsSystem().apply(
                new ActiveEffect(activeSkill.id(), activeSkill.name(), activeSkill.modifiedStat(), amount, expiresAt));
        evicted.ifPresent(effect -> DomainEventPublisher.publish(new CharacterEffectExpired(target, effect)));
        return new CastOutcome(true, amount, target.getCurrentHealth(), target.getMaxHealth(), false, false, expiresAt);
    }

    private boolean rollSkillHit(AbstractCharacter target) {
        double hitChance = CombatFormulas.hitChance(character.getStatSystem().getEffective(ModifiedStat.ACCURACY),
                target.getStatSystem().getEffective(ModifiedStat.EVASION));
        return Randomizer.rollChance(hitChance);
    }

    private boolean applyDamage(AbstractCharacter defender, int damage) {
        return defender.takeDamage(damage, character);
    }

    public record CastOutcome(boolean hit, int amount, int targetHealthAfter, int targetMaxHealth,
            boolean targetDefeated, boolean selfHeal, Instant effectExpiresAt) {
    }

    public record AttackRollOutcome(boolean hit, int amount) {
    }

    public CastRequestOutcome castSkill(ActiveSkill activeSkill, AbstractCharacter target) {
        if (!hasSkill(activeSkill)) {
            return new CastRequestOutcome.SkillUnknown(activeSkill.name());
        }
        if (target == null) {
            return new CastRequestOutcome.NoTarget();
        }
        if (target instanceof AbstractNpc && activeSkill.effect() == SkillEffectType.DAMAGE) {
            return new CastRequestOutcome.TargetInvalid(target.getId());
        }
        if (activeSkill.range() > 0 && character.getMotionSystem().getPosition()
                .distanceTo(target.getMotionSystem().getPosition()) > activeSkill.range()) {
            return new CastRequestOutcome.OutOfRange(activeSkill.name(), target.getName());
        }
        if (!isReady(activeSkill.id())) {
            return new CastRequestOutcome.OnCooldown(activeSkill.name(),
                    remainingCooldown(activeSkill.id()).toMillis());
        }
        if (character.getCurrentMana() < activeSkill.manaCost()) {
            return new CastRequestOutcome.InsufficientMana(activeSkill.name(), activeSkill.manaCost(),
                    character.getCurrentMana());
        }

        DomainEventPublisher.publish(new SkillCastBegin(character, activeSkill, target));
        return new CastRequestOutcome.Started();
    }

    public sealed interface CastRequestOutcome {

        record Started() implements CastRequestOutcome {
        }

        record SkillUnknown(String skillName) implements CastRequestOutcome {
        }

        record NoTarget() implements CastRequestOutcome {
        }

        record TargetInvalid(UUID targetId) implements CastRequestOutcome {
        }

        record OutOfRange(String skillName, String targetName) implements CastRequestOutcome {
        }

        record OnCooldown(String skillName, long remainingMs) implements CastRequestOutcome {
        }

        record InsufficientMana(String skillName, int required, int current) implements CastRequestOutcome {
        }
    }
}
