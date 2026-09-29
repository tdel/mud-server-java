package app.domain.actor.system;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import app.domain.ActiveEffect;
import app.domain.PassiveSkill.PassiveModifier;
import app.domain.PeriodicDamage;
import app.domain.PassiveSkill;
import app.domain.ActiveSkill;
import app.domain.SkillDamageType;
import app.domain.SkillEffectDefinition;
import app.domain.SkillEffectType;
import app.domain.SkillElement;
import app.domain.StatModifier;
import app.domain.StatOperator;
import app.domain.actor.AbstractCharacter;
import app.domain.actor.AbstractNpc;
import app.domain.actor.Attribute;
import app.domain.actor.ModifiedStat;
import app.domain.actor.event.CharacterBeginAttack;
import app.domain.actor.event.CharacterEffectExpired;
import app.domain.actor.event.DomainEventPublisher;
import app.domain.actor.event.SkillCastBegin;
import app.domain.actor.instance.PlayerInstance;
import app.domain.item.ArmorCategory;
import app.domain.item.EquipmentSlot;
import app.domain.item.Item;
import app.domain.item.ItemGrade;
import app.domain.item.WeaponType;
import app.game.Randomizer;
import app.game.catalog.PassiveSkillCatalogHolder;
import app.game.combat.CombatFormulas;
import app.game.engine.SkillCastEngine;

public final class SkillSystem {

    // Level effectif d'un sort octroyé par un objet équipé mais jamais appris.
    private static final int GRANTED_SKILL_LEVEL = 1;
    // Plafond de réussite d'un coup "blow", même dans le dos de la cible.
    private static final double BLOW_MAX_CHANCE = 0.80;

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

    // Seul PlayerInstance a des objets équipés susceptibles d'accorder des
    // sorts ; MonsterInstance/AbstractNpc n'en accordent jamais.
    public Set<ActiveSkill> getGrantedSkills() {
        if (!(character instanceof PlayerInstance player)) {
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

    // Somme des modificateurs ADDITIVE des passifs de stats connus (Weapon
    // Mastery, Armor Mastery, Anti Magic...) sur ce stat — ceux conditionnés à un
    // type d'armure ne comptent que si le plastron porté en est.
    public int passiveAdditive(ModifiedStat stat) {
        int sum = 0;
        for (StatModifier modifier : activePassiveModifiers(stat, StatOperator.ADDITIVE)) {
            sum += modifier.value();
        }
        return sum;
    }

    // Produit de (1 + value/100) des modificateurs MULTIPLICATIVE des passifs
    // connus (et applicables à l'armure portée) sur ce stat — 1.0 si aucun.
    public double passiveFactor(ModifiedStat stat) {
        double factor = 1.0;
        for (StatModifier modifier : activePassiveModifiers(stat, StatOperator.MULTIPLICATIVE)) {
            factor *= 1.0 + modifier.value() / 100.0;
        }
        return factor;
    }

    private List<StatModifier> activePassiveModifiers(ModifiedStat stat, StatOperator operator) {
        List<StatModifier> active = new ArrayList<>();
        ArmorCategory chestArmor = null;
        boolean chestResolved = false;
        for (Map.Entry<UUID, Integer> entry : knownPassiveSkillLevels.entrySet()) {
            for (PassiveModifier passive : PassiveSkillCatalogHolder.getById(entry.getKey())
                    .modifiersAt(entry.getValue())) {
                StatModifier modifier = passive.modifier();
                if (modifier.stat() != stat || modifier.operator() != operator) {
                    continue;
                }
                if (passive.conditional() && !chestResolved) {
                    chestArmor = chestArmor();
                    chestResolved = true;
                }
                if (passive.appliesTo(chestArmor)) {
                    active.add(modifier);
                }
            }
        }
        return active;
    }

    // Type d'armure du plastron porté (celui qui fait foi dans L2), null sans
    // plastron.
    private ArmorCategory chestArmor() {
        return character.getInventorySystem().getEquippedItems().stream()
                .filter(item -> item.getSlot() == EquipmentSlot.CHEST).findFirst().map(Item::getArmorCategory)
                .orElse(null);
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

    public CastOutcome cast(ActiveSkill activeSkill, int level, AbstractCharacter target, boolean shotCharged) {
        CastOutcome outcome = switch (activeSkill.skillType()) {
            case HEALING -> castHeal(activeSkill, level, target, shotCharged);
            case DAMAGE -> castDamage(activeSkill, level, target, shotCharged);
            case BUFF -> castModifier(activeSkill, level, target, false);
            case DEBUFF -> castModifier(activeSkill, level, target, true);
            case CURE -> castCure(target);
            case PASSIVE -> throw new IllegalStateException(
                    "ActiveSkill " + activeSkill.id() + " (" + activeSkill.name() + ") est PASSIVE, non castable");
            case TELEPORT -> throw new IllegalStateException("ActiveSkill " + activeSkill.id() + " ("
                    + activeSkill.name() + ") est TELEPORT, résolu par EscapeEngine");
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
    public AttackRollOutcome rollDamage(ActiveSkill activeSkill, int level, AbstractCharacter target,
            boolean shotCharged) {
        if (!rollSkillHit(target)) {
            return new AttackRollOutcome(false, 0);
        }
        if (activeSkill.blowChance() > 0 && !rollBlow(activeSkill, target)) {
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
                    target.getStatSystem().getEffective(ModifiedStat.PDEF), critical, shotCharged);
        } else {
            // calcMagicDam L2J : le power du sort est un facteur multiplicatif du
            // ratio sqrt(m.atk)/m.def, pas additif comme au physique.
            int magicalAttack = character.getStatSystem().getEffective(ModifiedStat.MATK);
            amount = CombatFormulas.resolveMagicalDamage(magicalAttack,
                    target.getStatSystem().getEffective(ModifiedStat.MDEF), activeSkill.powerAt(level), critical,
                    shotCharged);
        }
        if (activeSkill.element() != SkillElement.NONE) {
            amount = CombatFormulas.applyElementalResistance(amount,
                    target.getInventorySystem().getElementalResistance(activeSkill.element()));
        }
        return new AttackRollOutcome(true, amount);
    }

    // Coup "blow" (Mortal Blow, calcBlow L2J) : chance de base modulée par la
    // DEX du lanceur et sa position — x1.5 de flanc, x2 dans le dos de la cible
    // (angle entre l'orientation de la cible et la direction du lanceur).
    private boolean rollBlow(ActiveSkill activeSkill, AbstractCharacter target) {
        double toCaster = target.getMotionSystem().getPosition().headingTo(character.getMotionSystem().getPosition());
        double diff = Math.abs(Math.IEEEremainder(toCaster - target.getMotionSystem().getHeading(), 2 * Math.PI));
        double sideMod = diff > 2 * Math.PI / 3 ? 2.0 : diff > Math.PI / 3 ? 1.5 : 1.0;
        double chance = activeSkill.blowChance() / 100.0
                * CombatFormulas.statBonus(character.getAttributeSystem().getAttribute(Attribute.DEX)) * sideMod;
        return Randomizer.rollChance(Math.min(chance, BLOW_MAX_CHANCE));
    }

    public CastOutcome applyDamageOutcome(ActiveSkill activeSkill, AttackRollOutcome roll, AbstractCharacter target) {
        DomainEventPublisher.publish(new CharacterBeginAttack(character, target, false));
        // Cible devenue intouchable en vol (fin d'un Scroll of Escape, cf.
        // CombatSystem.isTeleporting) : le projectile la manque.
        if (!roll.hit() || target.getCombatSystem().isTeleporting()) {
            return new CastOutcome(false, 0, target.getResourceSystem().getCurrentHealth(),
                    target.getResourceSystem().getMaxHealth(), false, false, null, List.of(), 0);
        }
        int healthBefore = target.getResourceSystem().getCurrentHealth();
        boolean defeated = applyDamage(target, roll.amount());
        // Vampiric Touch : une part des PV réellement retirés (pas l'excédent d'un
        // coup fatal) revient au lanceur.
        int drained = activeSkill.drainPercent() > 0
                ? character.getResourceSystem()
                        .heal(Math.min(roll.amount(), healthBefore) * activeSkill.drainPercent() / 100)
                : 0;
        return new CastOutcome(true, roll.amount(), target.getResourceSystem().getCurrentHealth(),
                target.getResourceSystem().getMaxHealth(), defeated, false, null, List.of(), drained);
    }

    // Formule L2J (cf. CombatFormulas.resolveHeal) : pas de mitigation par une
    // stat de défense de la cible, un heal n'est jamais résisté.
    private CastOutcome castHeal(ActiveSkill activeSkill, int level, AbstractCharacter target, boolean shotCharged) {
        int healPower = CombatFormulas.resolveHeal(activeSkill.powerAt(level),
                character.getStatSystem().getEffective(ModifiedStat.MATK), shotCharged);
        int amount = target.getResourceSystem().heal(healPower);
        return new CastOutcome(true, amount, target.getResourceSystem().getCurrentHealth(),
                target.getResourceSystem().getMaxHealth(), false, target == character, null, List.of(), 0);
    }

    private CastOutcome castDamage(ActiveSkill activeSkill, int level, AbstractCharacter target, boolean shotCharged) {
        return applyDamageOutcome(activeSkill, rollDamage(activeSkill, level, target, shotCharged), target);
    }

    // Cure Poison : retire les poisons de la cible (amount = nombre d'effets
    // retirés, hit = au moins un). Les expirations sont publiées ici pour que
    // clients et persistance suivent comme pour une fin naturelle.
    private CastOutcome castCure(AbstractCharacter target) {
        List<ActiveEffect> cured = target.getEffectsSystem().removePeriodicDamage();
        cured.forEach(effect -> DomainEventPublisher.publish(new CharacterEffectExpired(target, effect)));
        return new CastOutcome(!cured.isEmpty(), cured.size(), target.getResourceSystem().getCurrentHealth(),
                target.getResourceSystem().getMaxHealth(), false, target == character, null, List.of(), 0);
    }

    // Un skill BUFF/DEBUFF "pur" ne porte qu'une seule entrée dans effects() : sa
    // magnitude vient de power(level) du sort (comme avant l'introduction du
    // schéma par level), le poids de chaque StatModifier.value() permet de
    // répartir cette magnitude sur plusieurs stats si besoin.
    private CastOutcome castModifier(ActiveSkill activeSkill, int level, AbstractCharacter target, boolean debuff) {
        if (debuff && !landsDebuff(activeSkill, target)) {
            return new CastOutcome(false, 0, target.getResourceSystem().getCurrentHealth(),
                    target.getResourceSystem().getMaxHealth(), false, false, null, List.of(), 0);
        }

        SkillEffectDefinition definition = activeSkill.effects().get(0);
        int magnitude = debuff ? -activeSkill.powerAt(level) : activeSkill.powerAt(level);
        List<StatModifier> modifiers = definition.effect().stream()
                .map(modifier -> new StatModifier(modifier.stat(), magnitude * modifier.value(), modifier.operator()))
                .toList();
        // Poison (dotInterval > 0) : power(level) PV perdus à chaque période.
        PeriodicDamage periodicDamage = definition.dotInterval() > 0
                ? new PeriodicDamage(activeSkill.powerAt(level), definition.dotInterval() * 1000L, character)
                : null;
        Instant expiresAt = Instant.now().plusSeconds(definition.time());
        Optional<ActiveEffect> evicted = target.getEffectsSystem().apply(new ActiveEffect(activeSkill.id(),
                activeSkill.name(), modifiers, expiresAt, periodicDamage, activeSkill.breakOnAction()));
        evicted.ifPresent(effect -> DomainEventPublisher.publish(new CharacterEffectExpired(target, effect)));
        return new CastOutcome(true, magnitude, target.getResourceSystem().getCurrentHealth(),
                target.getResourceSystem().getMaxHealth(), false, false, expiresAt, modifiers, 0);
    }

    // Sans landRate (règle historique) : jet de touche puis résistance MEN. Avec
    // landRate (sorts L2 : Curse: Weakness 80 %, Curse: Poison 70 %) : chance de
    // base réduite par l'attribut de résistance de la cible, sans jet de touche.
    private boolean landsDebuff(ActiveSkill activeSkill, AbstractCharacter target) {
        double resist = CombatFormulas
                .debuffResistChance(target.getAttributeSystem().getAttribute(activeSkill.resistAttribute()));
        if (activeSkill.landRate() <= 0) {
            return rollSkillHit(target) && !Randomizer.rollChance(resist);
        }
        return Randomizer.rollChance(activeSkill.landRate() / 100.0 * (1 - resist));
    }

    private boolean rollSkillHit(AbstractCharacter target) {
        double hitChance = CombatFormulas.hitChance(character.getStatSystem().getEffective(ModifiedStat.ACCURACY),
                target.getStatSystem().getEffective(ModifiedStat.EVASION));
        return Randomizer.rollChance(hitChance);
    }

    private boolean applyDamage(AbstractCharacter defender, int damage) {
        return defender.takeDamage(damage, character);
    }

    // drained : PV rendus au lanceur (Vampiric Touch), 0 sinon.
    public record CastOutcome(boolean hit, int amount, int targetHealthAfter, int targetMaxHealth,
            boolean targetDefeated, boolean selfHeal, Instant effectExpiresAt, List<StatModifier> modifiers,
            int drained) {
    }

    public record AttackRollOutcome(boolean hit, int amount) {
    }

    public CastRequestOutcome castSkill(ActiveSkill activeSkill, AbstractCharacter selectedTarget) {
        if (!hasSkill(activeSkill)) {
            return new CastRequestOutcome.SkillUnknown(activeSkill.name());
        }
        // SELF/PARTY : toujours centré sur le lanceur, quelle que soit la sélection.
        AbstractCharacter target = switch (activeSkill.target()) {
            case SELF, PARTY -> character;
            case ONE, AOE -> selectedTarget;
        };
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
        if (target.getCombatSystem().isTeleporting() && (activeSkill.skillType() == SkillEffectType.DAMAGE
                || activeSkill.skillType() == SkillEffectType.DEBUFF)) {
            return new CastRequestOutcome.TargetInvalid(target.getId());
        }
        if (activeSkill.skillType() == SkillEffectType.TELEPORT) {
            // Jamais lancé directement : uniquement via son parchemin (castFromItem).
            return new CastRequestOutcome.SkillUnknown(activeSkill.name());
        }
        if (!activeSkill.requiredWeapons().isEmpty() && character instanceof PlayerInstance player) {
            WeaponType wielded = player.getInventorySystem().getEquippedWeapon().map(Item::getWeaponType).orElse(null);
            if (wielded == null || !activeSkill.requiredWeapons().contains(wielded)) {
                return new CastRequestOutcome.WeaponRequired(activeSkill.name(), activeSkill.requiredWeapons());
            }
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
        if (character.getResourceSystem().getCurrentMana() < activeSkill.manaCostAt(level)) {
            return new CastRequestOutcome.InsufficientMana(activeSkill.name(), activeSkill.manaCostAt(level),
                    character.getResourceSystem().getCurrentMana());
        }

        DomainEventPublisher.publish(new SkillCastBegin(character, activeSkill, level, target));
        return new CastRequestOutcome.Started();
    }

    // Sort porté par un consommable (Scroll of Escape -> Teleport) : ni appris ni
    // soumis à sa propre recharge (le délai est celui de l'objet, cf.
    // InventorySystem.isItemReady), lancé sur soi-même au level 1.
    public void castFromItem(ActiveSkill activeSkill) {
        DomainEventPublisher.publish(new SkillCastBegin(character, activeSkill, GRANTED_SKILL_LEVEL, character));
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

        record WeaponRequired(String skillName, Set<WeaponType> weaponTypes) implements CastRequestOutcome {
        }
    }
}
