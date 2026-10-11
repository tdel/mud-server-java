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

import app.domain.AbnormalStatus;
import app.domain.ActiveEffect;
import app.domain.CureKind;
import app.domain.EffectCategory;
import app.domain.PeriodicKind;
import app.domain.SkillLevel;
import app.domain.SkillTargetType;
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
import app.domain.actor.event.CharacterEffectApplied;
import app.domain.actor.event.CharacterEffectExpired;
import app.domain.actor.event.DomainEventPublisher;
import app.domain.actor.event.SkillCastBegin;
import app.domain.actor.instance.MonsterInstance;
import app.domain.actor.instance.PlayerInstance;
import app.domain.actor.instance.SummonInstance;
import app.domain.item.ArmorCategory;
import app.domain.item.EquipmentSlot;
import app.domain.item.Item;
import app.domain.item.ItemGrade;
import app.domain.item.WeaponType;
import app.game.Randomizer;
import app.game.catalog.PassiveSkillCatalogHolder;
import app.game.combat.CombatFormulas;
import app.game.combat.DamageModifiers;
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
        WeaponType weapon = null;
        boolean resting = false;
        boolean moving = false;
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
                    weapon = wieldedWeapon();
                    // Au repos : Relax actif (seul effet breakOnAction sans statut).
                    resting = character.getEffectsSystem().isResting();
                    moving = character.getMotionSystem().getActiveMovement() != null;
                    chestResolved = true;
                }
                if (passive.appliesTo(chestArmor, weapon, resting, moving)) {
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

    // Famille de l'arme en main, null à mains nues (les monstres n'en ont pas).
    public WeaponType wieldedWeapon() {
        return character.getInventorySystem().getEquippedWeapon().map(Item::getWeaponType).orElse(null);
    }

    public boolean hasShield() {
        return character.getInventorySystem().getEquippedItems().stream()
                .anyMatch(item -> item.getSlot() == EquipmentSlot.OFF_HAND);
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
            case TOGGLE -> castToggle(activeSkill, level);
            case CURE -> castCure(activeSkill, target);
            case RECHARGE -> castRecharge(activeSkill, level, target);
            case PASSIVE -> throw new IllegalStateException(
                    "ActiveSkill " + activeSkill.id() + " (" + activeSkill.name() + ") est PASSIVE, non castable");
            case TELEPORT, SUMMON, CUBIC, RESURRECT, AGGRO, PEACE, BLINK ->
                throw new IllegalStateException("ActiveSkill " + activeSkill.id() + " (" + activeSkill.name() + ") est "
                        + activeSkill.skillType() + ", résolu par un moteur (cf. SkillCastEngine)");
        };

        markCooldown(activeSkill);
        return outcome;
    }

    // Quick Recycle (MAGIC_REUSE) raccourcit la recharge des sorts magiques.
    public void markCooldown(ActiveSkill activeSkill) {
        int reuseMs = activeSkill.magic()
                ? character.getStatSystem().adjust(ModifiedStat.MAGIC_REUSE, activeSkill.reuseTimeMs())
                : activeSkill.reuseTimeMs();
        nextCastAt.put(activeSkill.id(), Instant.now().plusMillis(Math.max(0, reuseMs)));
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
        boolean physical = activeSkill.damageType() == SkillDamageType.PHYSICAL;
        // Ultimate Evasion : chance d'esquiver purement une technique physique.
        if (physical && Randomizer.rollChance(target.getStatSystem().adjust(ModifiedStat.SKILL_EVASION, 0) / 100.0)) {
            return new AttackRollOutcome(false, 0);
        }
        if (activeSkill.blowChance() > 0 && !rollBlow(activeSkill, target)) {
            return new AttackRollOutcome(false, 0);
        }
        boolean critical = physical
                ? Randomizer.rollChance(DamageModifiers.physicalCritChance(character, target))
                : Randomizer.rollChance(
                        CombatFormulas.critChance(character.getStatSystem().getEffective(ModifiedStat.MCRIT)));
        int amount;
        if (physical) {
            // calcPhysDam L2J : le power du sort s'ajoute au p.atk avant le ratio
            // atk/def, ce qui préserve la progression entre levels d'un même sort.
            int skillPower = character.getStatSystem().getEffective(ModifiedStat.PATK) + activeSkill.powerAt(level);
            amount = CombatFormulas.resolvePhysicalDamage(skillPower, DamageModifiers.defenseWithBlock(target),
                    critical, shotCharged);
            amount = DamageModifiers.applyPhysical(character, target, amount, critical);
        } else {
            // calcMagicDam L2J : le power du sort est un facteur multiplicatif du
            // ratio sqrt(m.atk)/m.def, pas additif comme au physique.
            int magicalAttack = character.getStatSystem().getEffective(ModifiedStat.MATK);
            amount = CombatFormulas.resolveMagicalDamage(magicalAttack,
                    target.getStatSystem().getEffective(ModifiedStat.MDEF), activeSkill.powerAt(level), critical,
                    shotCharged);
            amount = DamageModifiers.applyMagical(character, target, amount);
        }
        if (activeSkill.element() != SkillElement.NONE) {
            amount = CombatFormulas.applyElementalResistance(amount, target.elementalResistance(activeSkill.element()));
        }
        // Deadly Blow / Backstab : chance de réduire d'un coup la cible (monstre) à
        // la moitié de ses PV.
        if (activeSkill.halfLethal() > 0 && target instanceof MonsterInstance
                && Randomizer.rollChance(activeSkill.halfLethal() / 100.0)) {
            amount = Math.max(amount, target.getResourceSystem().getCurrentHealth() / 2);
        }
        return new AttackRollOutcome(true, amount);
    }

    // Coup "blow" (Mortal Blow, calcBlow L2J) : chance de base modulée par la
    // DEX du lanceur et sa position — x1.5 de flanc, x2 dans le dos de la cible
    // (angle entre l'orientation de la cible et la direction du lanceur).
    private boolean rollBlow(ActiveSkill activeSkill, AbstractCharacter target) {
        double sideMod = isBehind(target) ? 2.0 : isFlank(target) ? 1.5 : 1.0;
        double chance = activeSkill.blowChance() / 100.0
                * Attribute.DEX.bonus(character.getAttributeSystem().getAttribute(Attribute.DEX)) * sideMod;
        return Randomizer.rollChance(Math.min(chance, BLOW_MAX_CHANCE));
    }

    private double angleFromTargetFacing(AbstractCharacter target) {
        double toCaster = target.getMotionSystem().getPosition().headingTo(character.getMotionSystem().getPosition());
        return Math.abs(Math.IEEEremainder(toCaster - target.getMotionSystem().getHeading(), 2 * Math.PI));
    }

    // Dans le dos de la cible (Backstab) : plus de 120° de son regard.
    public boolean isBehind(AbstractCharacter target) {
        return angleFromTargetFacing(target) > 2 * Math.PI / 3;
    }

    private boolean isFlank(AbstractCharacter target) {
        return angleFromTargetFacing(target) > Math.PI / 3;
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
    // stat de défense de la cible, un heal n'est jamais résisté. Un soin sur
    // cadavre (Corpse Life Drain) rend au lanceur un montant fixe.
    private CastOutcome castHeal(ActiveSkill activeSkill, int level, AbstractCharacter target, boolean shotCharged) {
        int healPower = activeSkill.target() == SkillTargetType.CORPSE
                ? activeSkill.powerAt(level)
                : CombatFormulas.resolveHeal(activeSkill.powerAt(level),
                        character.getStatSystem().getEffective(ModifiedStat.MATK), shotCharged);
        int amount = target.getResourceSystem().heal(healPower);
        return new CastOutcome(true, amount, target.getResourceSystem().getCurrentHealth(),
                target.getResourceSystem().getMaxHealth(), false, target == character, null, List.of(), 0);
    }

    private CastOutcome castDamage(ActiveSkill activeSkill, int level, AbstractCharacter target, boolean shotCharged) {
        return applyDamageOutcome(activeSkill, rollDamage(activeSkill, level, target, shotCharged), target);
    }

    // Cure Poison / Remedy / Servitor Cure : retire de la cible ses poisons et/ou
    // saignements (amount = nombre d'effets retirés, hit = au moins un). Les
    // expirations sont publiées ici pour que clients et persistance suivent comme
    // pour une fin naturelle.
    private CastOutcome castCure(ActiveSkill activeSkill, AbstractCharacter target) {
        CureKind cure = activeSkill.cureKind() == null ? CureKind.POISON : activeSkill.cureKind();
        List<ActiveEffect> cured = target.getEffectsSystem().removePeriodicDamage(cure);
        cured.forEach(effect -> DomainEventPublisher.publish(new CharacterEffectExpired(target, effect)));
        return new CastOutcome(!cured.isEmpty(), cured.size(), target.getResourceSystem().getCurrentHealth(),
                target.getResourceSystem().getMaxHealth(), false, target == character, null, List.of(), 0);
    }

    // Body To Mind / Servitor Recharge : rend power(level) PM à la cible, plus
    // son MANA_CHARGE (Higher Mana Gain). amount = PM réellement rendus.
    private CastOutcome castRecharge(ActiveSkill activeSkill, int level, AbstractCharacter target) {
        int amount = target.getResourceSystem()
                .gainMana(activeSkill.powerAt(level) + target.getStatSystem().adjust(ModifiedStat.MANA_CHARGE, 0));
        return new CastOutcome(true, amount, target.getResourceSystem().getCurrentHealth(),
                target.getResourceSystem().getMaxHealth(), false, target == character, null, List.of(), 0);
    }

    // Modificateurs posés par un buff/debuff/toggle au level donné : les
    // modificateurs littéraux du level (compétences de profession) s'ils
    // existent, sinon la règle historique : magnitude = power(level) (négative
    // pour un debuff), répartie selon le poids de chaque StatModifier de l'effet.
    private List<StatModifier> modifiersAt(ActiveSkill activeSkill, int level, boolean debuff) {
        SkillLevel skillLevel = activeSkill.levelAt(level);
        if (!skillLevel.modifiers().isEmpty()) {
            return skillLevel.modifiers();
        }
        if (activeSkill.effects().isEmpty()) {
            return List.of();
        }
        int magnitude = debuff ? -skillLevel.power() : skillLevel.power();
        return activeSkill.effects().get(0).effect().stream()
                .map(modifier -> new StatModifier(modifier.stat(), magnitude * modifier.value(), modifier.operator()))
                .toList();
    }

    // Un skill BUFF/DEBUFF "pur" ne porte qu'une seule entrée dans effects() : sa
    // durée, son statut éventuel (STUN, ROOT...) et ses dégâts périodiques
    // (poison, saignement : power(level) PV par période) ; ses modificateurs
    // viennent de modifiersAt.
    private CastOutcome castModifier(ActiveSkill activeSkill, int level, AbstractCharacter target, boolean debuff) {
        SkillEffectDefinition definition = activeSkill.effects().get(0);
        if (debuff && !landsDebuff(activeSkill, target, definition)) {
            return new CastOutcome(false, 0, target.getResourceSystem().getCurrentHealth(),
                    target.getResourceSystem().getMaxHealth(), false, false, null, List.of(), 0);
        }

        List<StatModifier> modifiers = modifiersAt(activeSkill, level, debuff);
        PeriodicDamage periodicDamage = definition.dotInterval() > 0
                ? new PeriodicDamage(activeSkill.powerAt(level), definition.dotInterval() * 1000L, character,
                        definition.dotKind())
                : null;
        Instant expiresAt = Instant.now().plusSeconds(definition.time());
        ActiveEffect applied = new ActiveEffect(activeSkill.id(), activeSkill.name(), modifiers, expiresAt,
                periodicDamage, activeSkill.breakOnAction(), definition.type(), definition.status(), null, 0, null);
        applyEffect(target, applied);
        int magnitude = debuff ? -activeSkill.powerAt(level) : activeSkill.powerAt(level);
        return new CastOutcome(true, magnitude, target.getResourceSystem().getCurrentHealth(),
                target.getResourceSystem().getMaxHealth(), false, false, expiresAt, modifiers, 0);
    }

    // Toggle (Vicious Stance...) activé : effet sans échéance qui consomme
    // mpPerTick PM toutes les 5 s (cf. ActiveEffectEngine) jusqu'à ce qu'on le
    // relance (castSkill) ou que les PM manquent. Mort feinte : cesse au premier
    // geste.
    private CastOutcome castToggle(ActiveSkill activeSkill, int level) {
        SkillEffectDefinition definition = activeSkill.effects().get(0);
        List<StatModifier> modifiers = activeSkill.levelAt(level).modifiers();
        ActiveEffect applied = new ActiveEffect(activeSkill.id(), activeSkill.name(), modifiers, ActiveEffect.PERMANENT,
                null, definition.status() == AbnormalStatus.FAKE_DEATH, EffectCategory.BUFF, definition.status(), null,
                Math.max(1, activeSkill.levelAt(level).mpPerTick()), null);
        applyEffect(character, applied);
        return new CastOutcome(true, 0, character.getResourceSystem().getCurrentHealth(),
                character.getResourceSystem().getMaxHealth(), false, true, null, modifiers, 0);
    }

    // Pose un effet sur `target` et publie les événements (éviction éventuelle,
    // pose) ; un effet qui augmente les PV max (Battle Roar) rend aussitôt la
    // différence, comme le MaxHp heal=true de L2J.
    public void applyEffect(AbstractCharacter target, ActiveEffect effect) {
        int maxBefore = target.getResourceSystem().getMaxHealth();
        Optional<ActiveEffect> evicted = target.getEffectsSystem().apply(effect);
        evicted.ifPresent(old -> DomainEventPublisher.publish(new CharacterEffectExpired(target, old)));
        int gained = target.getResourceSystem().getMaxHealth() - maxBefore;
        if (gained > 0) {
            target.getResourceSystem().heal(gained);
        }
        DomainEventPublisher.publish(new CharacterEffectApplied(target, effect));
    }

    // Sans landRate (règle historique) : jet de touche puis résistance MEN. Avec
    // landRate (sorts L2 : Curse: Weakness 80 %, Sleep 80 %, Stun 50 %) : chance
    // de base réduite par l'attribut de résistance de la cible, sans jet de
    // touche. S'y ajoutent la résistance aux sorts d'altération (Anti Magic) et
    // la résistance de trait du statut ou des dégâts périodiques (Lionheart,
    // Mental Shield, Invigor). Requiem n'affecte que sa race cible.
    private boolean landsDebuff(ActiveSkill activeSkill, AbstractCharacter target, SkillEffectDefinition definition) {
        if (activeSkill.targetRace() != null && (target.getCreatureRace() == null
                || !activeSkill.targetRace().equals(target.getCreatureRace().name()))) {
            return false;
        }
        double resist = CombatFormulas
                .debuffResistChance(target.getAttributeSystem().getAttribute(activeSkill.resistAttribute()));
        double traitFactor = traitFactor(target, definition);
        if (activeSkill.landRate() <= 0) {
            return rollSkillHit(target) && !Randomizer.rollChance(resist) && Randomizer.rollChance(traitFactor);
        }
        return Randomizer.rollChance(activeSkill.landRate() / 100.0 * (1 - resist) * traitFactor);
    }

    // Effet secondaire à statut d'un sort de dégâts (Stun Attack) : même règle
    // qu'un debuff, avec le landRate/resistAttribute du sort.
    public boolean landsSecondaryStatus(ActiveSkill activeSkill, AbstractCharacter target,
            SkillEffectDefinition definition) {
        return landsDebuff(activeSkill, target, definition);
    }

    private static double traitFactor(AbstractCharacter target, SkillEffectDefinition definition) {
        int resist = target.getStatSystem().adjust(ModifiedStat.DEBUFF_RESIST, 0);
        if (definition.status() != null && definition.status().resistStat() != null) {
            resist += target.getStatSystem().adjust(definition.status().resistStat(), 0);
        }
        if (definition.dotInterval() > 0) {
            resist += target.getStatSystem().adjust(
                    definition.dotKind() == PeriodicKind.BLEED ? ModifiedStat.RESIST_BLEED : ModifiedStat.RESIST_POISON,
                    0);
        }
        return Math.clamp(1 - resist / 100.0, 0.0, 1.0);
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
        if (activeSkill.skillType() == SkillEffectType.TELEPORT && !knows(activeSkill.id())) {
            // Teleport du Scroll of Escape : uniquement via son parchemin
            // (castFromItem). Return (Prophet), appris, se lance normalement.
            return new CastRequestOutcome.SkillUnknown(activeSkill.name());
        }
        // Toggle déjà actif : le relancer le coupe, sans incantation ni coût.
        Optional<ActiveEffect> activeToggle = activeSkill.skillType() == SkillEffectType.TOGGLE
                ? character.getEffectsSystem().find(activeSkill.id())
                : Optional.empty();
        if (activeToggle.isPresent()) {
            character.getEffectsSystem().remove(activeSkill.id());
            DomainEventPublisher.publish(new CharacterEffectExpired(character, activeToggle.get()));
            return new CastRequestOutcome.ToggledOff(activeSkill.name());
        }
        if (character.getEffectsSystem().isIncapacitated()) {
            return new CastRequestOutcome.Incapacitated();
        }
        if (activeSkill.magic() && character.getEffectsSystem().hasStatus(AbnormalStatus.SILENCE)) {
            return new CastRequestOutcome.ConditionFailed(activeSkill.name(), "vous êtes réduit au silence");
        }
        AbstractCharacter target = resolveRequestTarget(activeSkill, selectedTarget);
        if (target == null) {
            return switch (activeSkill.target()) {
                case SERVITOR -> new CastRequestOutcome.ConditionFailed(activeSkill.name(), "aucun serviteur invoqué");
                case CORPSE -> new CastRequestOutcome.ConditionFailed(activeSkill.name(), "aucun cadavre à portée");
                default -> new CastRequestOutcome.NoTarget();
            };
        }
        if (activeSkill.target() == SkillTargetType.DEAD_PLAYER) {
            if (!(target instanceof PlayerInstance) || target.getResourceSystem().getCurrentHealth() > 0) {
                return new CastRequestOutcome.ConditionFailed(activeSkill.name(), "la cible doit être un joueur mort");
            }
        } else if (target != character && target.getResourceSystem().getCurrentHealth() <= 0) {
            return new CastRequestOutcome.TargetInvalid(target.getId());
        }
        boolean hostile = activeSkill.skillType() == SkillEffectType.DAMAGE
                || activeSkill.skillType() == SkillEffectType.DEBUFF || activeSkill.skillType() == SkillEffectType.AGGRO
                || activeSkill.skillType() == SkillEffectType.PEACE || activeSkill.skillType() == SkillEffectType.BLINK;
        boolean selfCentered = activeSkill.target().selfCentered();
        if (hostile && !selfCentered && (target instanceof AbstractNpc || target == character
                || target instanceof SummonInstance && character instanceof PlayerInstance)) {
            return new CastRequestOutcome.TargetInvalid(target.getId());
        }
        if (hostile && !selfCentered && target.getCombatSystem().isTeleporting()) {
            return new CastRequestOutcome.TargetInvalid(target.getId());
        }
        if (activeSkill.targetRace() != null && activeSkill.target() == SkillTargetType.ONE
                && (target.getCreatureRace() == null
                        || !activeSkill.targetRace().equals(target.getCreatureRace().name()))) {
            return new CastRequestOutcome.ConditionFailed(activeSkill.name(), "n'affecte que les morts-vivants");
        }
        if ((activeSkill.skillType() == SkillEffectType.AGGRO || activeSkill.skillType() == SkillEffectType.PEACE)
                && !selfCentered && !(target instanceof MonsterInstance)) {
            return new CastRequestOutcome.ConditionFailed(activeSkill.name(), "ne fonctionne que sur un monstre");
        }
        if (!activeSkill.requiredWeapons().isEmpty() && character instanceof PlayerInstance) {
            WeaponType wielded = wieldedWeapon();
            if (wielded == null || !activeSkill.requiredWeapons().contains(wielded)) {
                return new CastRequestOutcome.WeaponRequired(activeSkill.name(), activeSkill.requiredWeapons());
            }
        }
        if (activeSkill.shieldRequired() && character instanceof PlayerInstance && !hasShield()) {
            return new CastRequestOutcome.ConditionFailed(activeSkill.name(), "nécessite un bouclier");
        }
        if (activeSkill.behindOnly() && !isBehind(target)) {
            return new CastRequestOutcome.ConditionFailed(activeSkill.name(), "il faut être dans le dos de la cible");
        }
        if (activeSkill.skillType() == SkillEffectType.SUMMON && character instanceof PlayerInstance player
                && player.getSummon() != null) {
            return new CastRequestOutcome.ConditionFailed(activeSkill.name(), "vous avez déjà un serviteur");
        }
        if (!selfCentered && activeSkill.range() > 0 && character.getMotionSystem().getPosition()
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
        if (activeSkill.hpCostAt(level) > 0
                && character.getResourceSystem().getCurrentHealth() <= activeSkill.hpCostAt(level)) {
            return new CastRequestOutcome.ConditionFailed(activeSkill.name(), "pas assez de PV");
        }

        DomainEventPublisher.publish(new SkillCastBegin(character, activeSkill, level, target));
        return new CastRequestOutcome.Started();
    }

    // Cible au moment de la demande : la sélection pour ONE/AOE/DEAD_PLAYER, le
    // lanceur pour les sorts centrés sur lui (les cibles réelles d'une zone sont
    // calculées à la résolution, cf. SkillCastEngine), le serviteur pour
    // SERVITOR. CORPSE exige un cadavre à portée dès la demande.
    private AbstractCharacter resolveRequestTarget(ActiveSkill activeSkill, AbstractCharacter selectedTarget) {
        return switch (activeSkill.target()) {
            case SELF, PARTY, AURA, FRONT, DEAD_PARTY -> character;
            case ONE, AOE, DEAD_PLAYER -> selectedTarget;
            case SERVITOR -> character instanceof PlayerInstance player ? player.getSummon() : null;
            case CORPSE -> character.getMotionSystem().getCurrentMap()
                    .nearestCorpse(character.getMotionSystem().getPosition(), activeSkill.range()).isPresent()
                            ? character
                            : null;
        };
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

        // Étourdi, endormi, paralysé, terrorisé ou en mort feinte.
        record Incapacitated() implements CastRequestOutcome {
        }

        // Condition propre au sort non remplie (bouclier, dos de la cible,
        // serviteur, cadavre, silence...) ; reason est affichable telle quelle.
        record ConditionFailed(String skillName, String reason) implements CastRequestOutcome {
        }

        record ToggledOff(String skillName) implements CastRequestOutcome {
        }
    }
}
