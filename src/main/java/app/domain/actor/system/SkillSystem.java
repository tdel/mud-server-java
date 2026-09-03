package app.domain.actor.system;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import app.domain.ActiveEffect;
import app.domain.PassiveSkill;
import app.domain.ActiveSkill;
import app.domain.SkillDamageType;
import app.domain.SkillEffectDefinition;
import app.domain.SkillEffectType;
import app.domain.SkillElement;
import app.domain.StatModifier;
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
import app.game.catalog.PassiveSkillCatalogHolder;
import app.game.combat.CombatFormulas;
import app.game.engine.SkillCastEngine;

public final class SkillSystem {

    // Level effectif d'un sort octroyé par un objet équipé mais jamais appris.
    private static final int GRANTED_SKILL_LEVEL = 1;

    private final AbstractCharacter character;
    private final Map<UUID, Integer> knownSkillLevels = new ConcurrentHashMap<>();
    private final Map<UUID, Instant> nextCastAt = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> knownPassiveSkillLevels = new ConcurrentHashMap<>();
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
        return knownSkillLevels.containsKey(skillId);
    }

    public int levelOf(UUID skillId) {
        return knownSkillLevels.getOrDefault(skillId, 0);
    }

    public LearnResult learn(ActiveSkill activeSkill, int level) {
        Integer previous = knownSkillLevels.get(activeSkill.id());
        if (previous == null) {
            knownSkillLevels.put(activeSkill.id(), level);
            return LearnResult.NEW;
        }
        if (previous >= level) {
            return LearnResult.ALREADY_KNOWN;
        }
        knownSkillLevels.put(activeSkill.id(), level);
        return LearnResult.UPGRADED;
    }

    public enum LearnResult {
        NEW, UPGRADED, ALREADY_KNOWN
    }

    public Set<UUID> knownSkillIds() {
        return Set.copyOf(knownSkillLevels.keySet());
    }

    public Map<UUID, Integer> knownSkillLevels() {
        return Map.copyOf(knownSkillLevels);
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

    // Level courant du sort pour ce personnage : le level appris s'il en connaît
    // un, sinon le level de base (1) si le sort n'est qu'octroyé par un objet
    // équipé.
    public int effectiveLevel(ActiveSkill activeSkill) {
        return knows(activeSkill.id()) ? levelOf(activeSkill.id()) : GRANTED_SKILL_LEVEL;
    }

    public boolean learn(PassiveSkill passiveSkill, int level) {
        Integer previous = knownPassiveSkillLevels.get(passiveSkill.id());
        if (previous != null && previous >= level) {
            return false;
        }
        knownPassiveSkillLevels.put(passiveSkill.id(), level);
        return true;
    }

    public int passiveLevelOf(UUID passiveSkillId) {
        return knownPassiveSkillLevels.getOrDefault(passiveSkillId, 0);
    }

    public Map<UUID, Integer> knownPassiveSkillLevels() {
        return Map.copyOf(knownPassiveSkillLevels);
    }

    // Le grade débloqué est dérivé du level connu de chaque compétence passive
    // (ex: Expertise Grade), pas de son id — cf. PassiveSkill.gradeAt(level).
    public ItemGrade unlockedGrade() {
        return knownPassiveSkillLevels.entrySet().stream()
                .map(entry -> PassiveSkillCatalogHolder.getById(entry.getKey()).gradeAt(entry.getValue()))
                .max(Comparator.comparingInt(Enum::ordinal)).orElse(ItemGrade.NOGRADE);
    }

    public boolean isReady(UUID skillId) {
        return !Instant.now().isBefore(nextCastAt.getOrDefault(skillId, Instant.MIN));
    }

    public Duration remainingCooldown(UUID skillId) {
        Duration remaining = Duration.between(Instant.now(), nextCastAt.getOrDefault(skillId, Instant.MIN));
        return remaining.isNegative() ? Duration.ZERO : remaining;
    }

    public CastOutcome cast(ActiveSkill activeSkill, int level, AbstractCharacter target) {
        CastOutcome outcome = switch (activeSkill.skillType()) {
            case HEALING -> castHeal(activeSkill, level, target);
            case DAMAGE -> castDamage(activeSkill, level, target);
            case BUFF -> castModifier(activeSkill, level, target, false);
            case DEBUFF -> castModifier(activeSkill, level, target, true);
            case PASSIVE -> throw new IllegalStateException(
                    "ActiveSkill " + activeSkill.id() + " (" + activeSkill.name() + ") est PASSIVE, non castable");
        };

        markCooldown(activeSkill);
        return outcome;
    }

    public void markCooldown(ActiveSkill activeSkill) {
        nextCastAt.put(activeSkill.id(), Instant.now().plusMillis(activeSkill.reuseTimeMs()));
    }

    // Pour les sorts projectiles (cf. game.engine.ProjectileEngine) : le jet
    // d'attaque et les dégâts sont calculés à la fin de l'incantation, mais leur
    // application (PV décomptés, événement publié) est différée jusqu'à
    // l'impact.
    public AttackRollOutcome rollDamage(ActiveSkill activeSkill, int level, AbstractCharacter target) {
        if (!rollSkillHit(target)) {
            return new AttackRollOutcome(false, 0);
        }
        boolean physical = activeSkill.damageType() == SkillDamageType.PHYSICAL;
        ModifiedStat critStat = physical ? ModifiedStat.PCRIT : ModifiedStat.MCRIT;
        boolean critical = Randomizer.rollChance(character.getStatSystem().getEffective(critStat) / 100.0);
        int amount;
        if (physical) {
            // calcPhysDam L2J : le power du sort s'ajoute au p.atk avant le ratio
            // atk/def, ce qui préserve la progression entre levels d'un même sort.
            int skillPower = character.getStatSystem().getEffective(ModifiedStat.PATK) + activeSkill.powerAt(level);
            amount = CombatFormulas.resolvePhysicalDamage(skillPower,
                    target.getStatSystem().getEffective(ModifiedStat.PDEF), critical);
        } else {
            // calcMagicDam L2J : le power du sort est un facteur multiplicatif du
            // ratio sqrt(m.atk)/m.def, pas additif comme au physique.
            int magicalAttack = character.getStatSystem().getEffective(ModifiedStat.MATK);
            amount = CombatFormulas.resolveMagicalDamage(magicalAttack,
                    target.getStatSystem().getEffective(ModifiedStat.MDEF), activeSkill.powerAt(level), critical);
        }
        if (activeSkill.element() != SkillElement.NONE) {
            amount = CombatFormulas.applyElementalResistance(amount,
                    target.getElementalResistance(activeSkill.element()));
        }
        return new AttackRollOutcome(true, amount);
    }

    public CastOutcome applyDamageOutcome(AttackRollOutcome roll, AbstractCharacter target) {
        DomainEventPublisher.publish(new CharacterBeginAttack(character, target));
        if (!roll.hit()) {
            return new CastOutcome(false, 0, target.getCurrentHealth(), target.getMaxHealth(), false, false, null,
                    List.of());
        }
        boolean defeated = applyDamage(target, roll.amount());
        return new CastOutcome(true, roll.amount(), target.getCurrentHealth(), target.getMaxHealth(), defeated, false,
                null, List.of());
    }

    // Formule L2J (cf. CombatFormulas.resolveHeal) : pas de mitigation par une
    // stat de défense de la cible, un heal n'est jamais résisté.
    private CastOutcome castHeal(ActiveSkill activeSkill, int level, AbstractCharacter target) {
        int healPower = CombatFormulas.resolveHeal(activeSkill.powerAt(level),
                character.getStatSystem().getEffective(ModifiedStat.MATK));
        int amount = target.heal(healPower);
        return new CastOutcome(true, amount, target.getCurrentHealth(), target.getMaxHealth(), false,
                target == character, null, List.of());
    }

    private CastOutcome castDamage(ActiveSkill activeSkill, int level, AbstractCharacter target) {
        return applyDamageOutcome(rollDamage(activeSkill, level, target), target);
    }

    // Un skill BUFF/DEBUFF "pur" ne porte qu'une seule entrée dans effects() : sa
    // magnitude vient de power(level) du sort (comme avant l'introduction du
    // schéma par level), le poids de chaque StatModifier.value() permet de
    // répartir cette magnitude sur plusieurs stats si besoin.
    private CastOutcome castModifier(ActiveSkill activeSkill, int level, AbstractCharacter target, boolean debuff) {
        if (debuff && (!rollSkillHit(target)
                || Randomizer.rollChance(CombatFormulas.debuffResistChance(target.getAttribute(Attribute.MEN))))) {
            return new CastOutcome(false, 0, target.getCurrentHealth(), target.getMaxHealth(), false, false, null,
                    List.of());
        }

        SkillEffectDefinition definition = activeSkill.effects().get(0);
        int magnitude = debuff ? -activeSkill.powerAt(level) : activeSkill.powerAt(level);
        List<StatModifier> modifiers = definition.effect().stream()
                .map(modifier -> new StatModifier(modifier.stat(), magnitude * modifier.value(), modifier.operator()))
                .toList();
        Instant expiresAt = Instant.now().plusSeconds(definition.time());
        Optional<ActiveEffect> evicted = target.getEffectsSystem()
                .apply(new ActiveEffect(activeSkill.id(), activeSkill.name(), modifiers, expiresAt));
        evicted.ifPresent(effect -> DomainEventPublisher.publish(new CharacterEffectExpired(target, effect)));
        return new CastOutcome(true, magnitude, target.getCurrentHealth(), target.getMaxHealth(), false, false,
                expiresAt, modifiers);
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
            boolean targetDefeated, boolean selfHeal, Instant effectExpiresAt, List<StatModifier> modifiers) {
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
        if (target instanceof AbstractNpc && activeSkill.skillType() == SkillEffectType.DAMAGE) {
            return new CastRequestOutcome.TargetInvalid(target.getId());
        }
        if (target == character && (activeSkill.skillType() == SkillEffectType.DAMAGE
                || activeSkill.skillType() == SkillEffectType.DEBUFF)) {
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
        int level = effectiveLevel(activeSkill);
        if (character.getCurrentMana() < activeSkill.manaCostAt(level)) {
            return new CastRequestOutcome.InsufficientMana(activeSkill.name(), activeSkill.manaCostAt(level),
                    character.getCurrentMana());
        }

        DomainEventPublisher.publish(new SkillCastBegin(character, activeSkill, level, target));
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
