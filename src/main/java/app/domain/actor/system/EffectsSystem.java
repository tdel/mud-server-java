package app.domain.actor.system;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.stream.Stream;

import app.domain.AbnormalStatus;
import app.domain.ActiveEffect;
import app.domain.CureKind;
import app.domain.EffectCategory;
import app.domain.StatModifier;
import app.domain.StatOperator;
import app.domain.actor.AbstractCharacter;
import app.domain.actor.ModifiedStat;
import app.domain.item.GradePenalty;

public final class EffectsSystem {

    public static final int MAX_BUFF_SLOTS = 6;
    public static final int MAX_DEBUFF_SLOTS = 4;

    private final AbstractCharacter character;
    private final Map<UUID, ActiveEffect> activeEffects = new ConcurrentHashMap<>();
    private final Map<UUID, Instant> nextPeriodicTickAt = new ConcurrentHashMap<>();

    public EffectsSystem(AbstractCharacter character) {
        this.character = character;
    }

    // Retourne l'effet évicté (le plus proche d'expirer, dans la même catégorie)
    // si l'application de ce nouvel effet a saturé les slots — vide sinon.
    // Renouveler un sort déjà actif (même skillId) n'évince jamais rien : ce
    // n'est pas un nouveau slot.
    public Optional<ActiveEffect> apply(ActiveEffect effect) {
        Optional<ActiveEffect> evicted = Optional.empty();
        if (!activeEffects.containsKey(effect.skillId()) && occupiesSlot(effect)) {
            evicted = evictIfFull(effect.category());
        }
        activeEffects.put(effect.skillId(), effect);
        return evicted;
    }

    // Les pénalités de grade (passives liées à l'équipement, comme dans L2), les
    // toggles et les cubiques n'occupent pas de slot et ne sont jamais évincés.
    private static boolean occupiesSlot(ActiveEffect effect) {
        return !GradePenalty.isGradePenalty(effect.skillId()) && !effect.isToggle() && effect.cubic() == null;
    }

    private Optional<ActiveEffect> evictIfFull(EffectCategory category) {
        int max = category == EffectCategory.BUFF ? MAX_BUFF_SLOTS : MAX_DEBUFF_SLOTS;
        List<ActiveEffect> sameCategory = active().stream()
                .filter(effect -> effect.category() == category && occupiesSlot(effect)).toList();
        if (sameCategory.size() < max) {
            return Optional.empty();
        }
        ActiveEffect soonestToExpire = sameCategory.stream().min(Comparator.comparing(ActiveEffect::expiresAt))
                .orElseThrow();
        activeEffects.remove(soonestToExpire.skillId(), soonestToExpire);
        return Optional.of(soonestToExpire);
    }

    // Somme de tous les modificateurs ADDITIVE actifs sur ce stat.
    public int additiveModifier(ModifiedStat stat) {
        return matchingModifiers(stat).filter(modifier -> modifier.operator() == StatOperator.ADDITIVE)
                .mapToInt(StatModifier::value).sum();
    }

    // Produit de (1 + value/100) pour chaque modificateur MULTIPLICATIVE actif sur
    // ce stat — 1.0 si aucun.
    public double multiplicativeFactor(ModifiedStat stat) {
        return matchingModifiers(stat).filter(modifier -> modifier.operator() == StatOperator.MULTIPLICATIVE)
                .mapToDouble(modifier -> 1.0 + modifier.value() / 100.0).reduce(1.0, (a, b) -> a * b);
    }

    private Stream<StatModifier> matchingModifiers(ModifiedStat stat) {
        Instant now = Instant.now();
        return activeEffects.values().stream().filter(effect -> now.isBefore(effect.expiresAt()))
                .flatMap(effect -> effect.modifiers().stream()).filter(modifier -> modifier.stat() == stat);
    }

    public List<ActiveEffect> active() {
        Instant now = Instant.now();
        return activeEffects.values().stream().filter(effect -> now.isBefore(effect.expiresAt())).toList();
    }

    public List<ActiveEffect> expireDue(Instant now) {
        List<ActiveEffect> expired = new ArrayList<>();
        for (ActiveEffect effect : activeEffects.values()) {
            if (!now.isBefore(effect.expiresAt())) {
                activeEffects.remove(effect.skillId(), effect);
                expired.add(effect);
            }
        }
        return expired;
    }

    public boolean isEmpty() {
        return activeEffects.isEmpty();
    }

    // Effets à dégâts périodiques (poison, saignement) ou à soin régulier
    // (Greater Heal) dont la prochaine période est échue : la période suivante
    // est planifiée d'office, l'appelant (ActiveEffectEngine) applique les
    // dégâts/soins.
    public List<ActiveEffect> duePeriodicDamage(Instant now) {
        return duePeriodic(now, effect -> effect.periodicDamage() != null ? effect.periodicDamage().intervalMs() : 0);
    }

    public List<ActiveEffect> duePeriodicHeal(Instant now) {
        return duePeriodic(now, effect -> effect.periodicHeal() != null ? effect.periodicHeal().intervalMs() : 0);
    }

    private List<ActiveEffect> duePeriodic(Instant now, java.util.function.ToLongFunction<ActiveEffect> interval) {
        List<ActiveEffect> due = new ArrayList<>();
        for (ActiveEffect effect : activeEffects.values()) {
            long intervalMs = interval.applyAsLong(effect);
            if (intervalMs <= 0 || !now.isBefore(effect.expiresAt())) {
                continue;
            }
            Instant next = nextPeriodicTickAt.computeIfAbsent(effect.skillId(), id -> now.plusMillis(intervalMs));
            if (!now.isBefore(next)) {
                nextPeriodicTickAt.put(effect.skillId(), next.plusMillis(intervalMs));
                due.add(effect);
            }
        }
        nextPeriodicTickAt.keySet().removeIf(id -> !activeEffects.containsKey(id));
        return due;
    }

    // Altérations d'état actives (STUN, ROOT...), cf. AbnormalStatus.
    public boolean hasStatus(AbnormalStatus status) {
        return active().stream().anyMatch(effect -> effect.status() == status);
    }

    // Au repos (assis) : Relax actif, seul effet breakOnAction sans statut.
    public boolean isResting() {
        return active().stream().anyMatch(effect -> effect.breaksOnAction() && effect.status() == null);
    }

    public boolean isIncapacitated() {
        return active().stream().anyMatch(effect -> effect.status() != null && effect.status().incapacitates());
    }

    public boolean canMove() {
        return active().stream().noneMatch(effect -> effect.status() != null && effect.status().blocksMovement());
    }

    // Retire (et renvoie) les effets portant ce statut (Sleep au premier dégât).
    public List<ActiveEffect> removeStatus(AbnormalStatus status) {
        return removeWhere(effect -> effect.status() == status);
    }

    public List<ActiveEffect> toggles() {
        return activeEffects.values().stream().filter(ActiveEffect::isToggle).toList();
    }

    public List<ActiveEffect> cubics() {
        return active().stream().filter(effect -> effect.cubic() != null).toList();
    }

    // Retire (et renvoie) les effets qui cessent au premier geste du porteur
    // (Relax) : déplacement, attaque ou incantation.
    public List<ActiveEffect> removeBreakingOnAction() {
        return removeWhere(ActiveEffect::breaksOnAction);
    }

    // Mort du porteur : retire (et renvoie) tous les effets à dégâts
    // périodiques.
    public List<ActiveEffect> removePeriodicDamage() {
        return removeWhere(effect -> effect.periodicDamage() != null);
    }

    // Cure Poison (POISON), Remedy (BLEED), Servitor Cure (ALL).
    public List<ActiveEffect> removePeriodicDamage(CureKind cure) {
        return removeWhere(effect -> effect.periodicDamage() != null && effect.periodicDamage().kind().curedBy(cure));
    }

    // Mort du porteur : comme dans L2, tous ses buffs et debuffs tombent (les
    // pénalités de grade, liées à l'équipement porté, restent).
    public List<ActiveEffect> removeAllOnDeath() {
        return removeWhere(effect -> !GradePenalty.isGradePenalty(effect.skillId()));
    }

    public Optional<ActiveEffect> find(UUID effectId) {
        return Optional.ofNullable(activeEffects.get(effectId));
    }

    private List<ActiveEffect> removeWhere(Predicate<ActiveEffect> predicate) {
        List<ActiveEffect> removed = new ArrayList<>();
        for (ActiveEffect effect : activeEffects.values()) {
            if (predicate.test(effect) && activeEffects.remove(effect.skillId(), effect)) {
                nextPeriodicTickAt.remove(effect.skillId());
                removed.add(effect);
            }
        }
        return removed;
    }

    public void remove(UUID effectId) {
        activeEffects.remove(effectId);
    }
}
