package app.game.engine;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import app.domain.ActiveSkill;
import app.domain.SkillEffectType;
import app.domain.SkillTargetType;
import app.domain.actor.AbstractCharacter;
import app.domain.actor.system.SkillSystem;
import app.domain.actor.event.DomainEventPublisher;
import app.domain.actor.event.SkillCast;
import app.domain.actor.event.SkillCastBegin;
import app.domain.actor.instance.CharacterInstance;
import app.domain.map.Position;
import app.network.message.ingame.CastResult;
import app.network.message.ingame.SkillCastAnnounced;
import app.network.message.ingame.SkillCastCancelled;
import app.network.message.ingame.SkillCastStarted;
import app.network.message.ingame.SkillFizzled;
import app.network.message.ingame.SkillModifierAnnounced;
import app.network.message.ingame.SkillOnCooldown;

@Component
public class SkillCastEngine {

    private static final Logger log = LoggerFactory.getLogger(SkillCastEngine.class);

    private static final long TICK_INTERVAL_MS = 100L;

    private final Map<UUID, AbstractCharacter> casting = new ConcurrentHashMap<>();
    private final MovementEngine movementEngine;
    private final ProjectileEngine projectileEngine;

    public SkillCastEngine(MovementEngine movementEngine, ProjectileEngine projectileEngine) {
        this.movementEngine = movementEngine;
        this.projectileEngine = projectileEngine;
    }

    @EventListener
    void onSkillCastBegin(SkillCastBegin event) {
        beginCast(event.caster(), event.activeSkill(), event.level(), event.target());
    }

    private void beginCast(AbstractCharacter caster, ActiveSkill activeSkill, int level, AbstractCharacter target) {
        // On incante sans marcher : le déplacement en cours est arrêté ici (avant même
        // le délai d'incantation) plutôt qu'à la résolution différée du sort
        // (resolveCast) — trop tard, le client continue sinon d'interpoler le
        // déplacement pendant toute la durée de l'incantation.
        movementEngine.stopMovement(caster);
        caster.getSkillSystem().updateCast(new ActiveCast(activeSkill, level, target, System.nanoTime(),
                activeSkill.castingTimeMs() * 1_000_000L));
        casting.put(caster.getId(), caster);
        log.debug("activeSkill.cast_started thread={} caster={} activeSkill={} castingTimeMs={}",
                Thread.currentThread().getName(), caster.getId(), activeSkill.name(), activeSkill.castingTimeMs());
        caster.broadcast(new SkillCastStarted(caster.getId(), caster.getName(), activeSkill.id(), activeSkill.name(),
                target.getId(), target.getName(), activeSkill.castingTimeMs()), null);
    }

    public void cancelCast(AbstractCharacter caster) {
        ActiveCast activeCast = caster.getSkillSystem().getActiveCast();
        if (activeCast == null) {
            return;
        }
        caster.getSkillSystem().clearCast();
        casting.remove(caster.getId());
        log.debug("activeSkill.cast_cancelled thread={} caster={}", Thread.currentThread().getName(), caster.getId());
        caster.broadcast(new SkillCastCancelled(caster.getId(), caster.getName(), activeCast.activeSkill().id(),
                activeCast.activeSkill().name()), null);
    }

    @Scheduled(fixedRate = TICK_INTERVAL_MS)
    void tick() {
        long now = System.nanoTime();
        for (AbstractCharacter caster : casting.values()) {
            ActiveCast activeCast = caster.getSkillSystem().getActiveCast();
            if (activeCast == null) {
                casting.remove(caster.getId());
                continue;
            }
            if (now - activeCast.startedAtNanos() < activeCast.castingTimeNanos()) {
                continue;
            }
            casting.remove(caster.getId());
            caster.getSkillSystem().clearCast();
            try {
                resolveCast(caster, activeCast);
            } catch (Exception e) {
                log.error("activeSkill.cast_resolution_failed caster={}", caster.getId(), e);
            }
        }
    }

    private void resolveCast(AbstractCharacter caster, ActiveCast activeCast) {
        ActiveSkill activeSkill = activeCast.activeSkill();
        int level = activeCast.level();
        AbstractCharacter primaryTarget = activeCast.target();

        if (caster.getCurrentHealth() <= 0) {
            return;
        }
        if (!isTargetStillValid(caster, activeSkill, primaryTarget)) {
            caster.send(new SkillFizzled(activeSkill.id(), activeSkill.name(), "La cible n'est plus valide."));
            return;
        }
        if (!caster.trySpendMana(activeSkill.manaCostAt(level))) {
            caster.send(new SkillFizzled(activeSkill.id(), activeSkill.name(), "Plus assez de mana."));
            return;
        }

        // Passé ce point le sort va jusqu'au jet (hit ou miss) : le cooldown
        // s'applique dans tous les cas, il faut donc en informer le client ici.
        caster.send(new SkillOnCooldown(activeSkill.name(), activeSkill.reuseTimeMs()));

        if (activeSkill.skillType() == SkillEffectType.DAMAGE && activeSkill.projectile()) {
            SkillSystem.AttackRollOutcome roll = caster.getSkillSystem().rollDamage(activeSkill, level, primaryTarget);
            caster.getSkillSystem().markCooldown(activeSkill);
            DomainEventPublisher.publish(new SkillCast(caster, activeSkill, level, primaryTarget, roll.amount(), false,
                    roll.hit(), null, List.of()));
            projectileEngine.launch(caster, activeSkill, level, primaryTarget, roll);
            return;
        }

        List<AbstractCharacter> targets = activeSkill.target() == SkillTargetType.AOE
                ? resolveAoeTargets(caster, activeSkill, primaryTarget)
                : List.of(primaryTarget);

        // cast(...) marque le cooldown à chaque appel (une fois par cible touchée en
        // AOE) : les timestamps successifs sont assez proches pour ne pas dériver.
        boolean anyHit = false;
        for (AbstractCharacter target : targets) {
            if (target != primaryTarget && !isTargetStillValid(caster, activeSkill, target)) {
                continue;
            }
            anyHit |= resolveCastOnTarget(caster, activeSkill, level, target);
        }
        if (!anyHit && targets.size() > 1) {
            log.debug("activeSkill.aoe_no_target_hit caster={} activeSkill={}", caster.getId(), activeSkill.name());
        }
    }

    // Applique le sort sur UNE cible (mono-cible, ou une cible d'un groupe AOE) et
    // diffuse les messages réseau correspondants. Retourne si la cible a
    // effectivement été touchée.
    private boolean resolveCastOnTarget(AbstractCharacter caster, ActiveSkill activeSkill, int level,
            AbstractCharacter target) {
        SkillSystem.CastOutcome outcome = caster.getSkillSystem().cast(activeSkill, level, target);
        DomainEventPublisher.publish(new SkillCast(caster, activeSkill, level, target, outcome.amount(),
                outcome.targetDefeated(), outcome.hit(), outcome.effectExpiresAt(), outcome.modifiers()));
        if (outcome.hit()) {
            SkillEffectApplier.applySecondaryEffects(activeSkill, target);
        }

        if (activeSkill.skillType() == SkillEffectType.BUFF || activeSkill.skillType() == SkillEffectType.DEBUFF) {
            boolean beneficial = activeSkill.skillType() == SkillEffectType.BUFF;
            int durationSeconds = activeSkill.effects().isEmpty() ? 0 : activeSkill.effects().get(0).time();
            caster.broadcast(new SkillModifierAnnounced(caster.getId(), caster.getName(), activeSkill.id(),
                    activeSkill.name(), target.getId(), target.getName(), target == caster, beneficial, outcome.hit(),
                    outcome.modifiers(), outcome.amount(), durationSeconds, activeSkill.manaCostAt(level),
                    caster.getCurrentMana(), caster.getMaxMana()), null);
            return outcome.hit();
        }

        caster.send(new CastResult(activeSkill.id(), activeSkill.name(), target.getId(), target.getName(),
                outcome.selfHeal(), outcome.hit(), outcome.amount(), outcome.targetHealthAfter(),
                outcome.targetMaxHealth(), outcome.targetDefeated(), activeSkill.manaCostAt(level),
                caster.getCurrentMana(), caster.getMaxMana()));
        caster.broadcast(
                new SkillCastAnnounced(caster.getId(), caster.getName(), activeSkill.id(), activeSkill.name(),
                        target.getId(), target.getName(), outcome.selfHeal(), outcome.hit(), outcome.amount(),
                        outcome.targetHealthAfter(), outcome.targetMaxHealth(), outcome.targetDefeated()),
                caster instanceof CharacterInstance player ? player : null);
        if (outcome.targetDefeated()) {
            caster.clearCombatTarget();
        }
        return outcome.hit();
    }

    private List<AbstractCharacter> resolveAoeTargets(AbstractCharacter caster, ActiveSkill activeSkill,
            AbstractCharacter primaryTarget) {
        double radius = activeSkill.aoeRadius() > 0 ? activeSkill.aoeRadius() : activeSkill.range();
        Position center = primaryTarget.getMotionSystem().getPosition();
        return caster.getMotionSystem().getCurrentMap().occupantsWithin(center, radius);
    }

    private boolean isTargetStillValid(AbstractCharacter caster, ActiveSkill activeSkill, AbstractCharacter target) {
        if (target == caster) {
            return true;
        }
        if (target.getCurrentHealth() <= 0 || !caster.getMotionSystem().getCurrentMap().isPresent(target)) {
            return false;
        }
        return activeSkill.range() <= 0 || caster.getMotionSystem().getPosition()
                .distanceTo(target.getMotionSystem().getPosition()) <= activeSkill.range();
    }

    public record ActiveCast(ActiveSkill activeSkill, int level, AbstractCharacter target, long startedAtNanos,
            long castingTimeNanos) {
    }
}
