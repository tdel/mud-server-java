package app.game.engine;

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
import app.domain.actor.AbstractCharacter;
import app.domain.actor.system.SkillSystem;
import app.domain.actor.event.DomainEventPublisher;
import app.domain.actor.event.SkillCast;
import app.domain.actor.event.SkillCastBegin;
import app.domain.actor.instance.CharacterInstance;
import app.network.message.ingame.CastResult;
import app.network.message.ingame.SkillCastAnnounced;
import app.network.message.ingame.SkillCastCancelled;
import app.network.message.ingame.SkillCastStarted;
import app.network.message.ingame.SkillFizzled;
import app.network.message.ingame.SkillModifierAnnounced;

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
        beginCast(event.caster(), event.activeSkill(), event.target());
    }

    private void beginCast(AbstractCharacter caster, ActiveSkill activeSkill, AbstractCharacter target) {
        // On incante sans marcher : le déplacement en cours est arrêté ici (avant même
        // le délai d'incantation) plutôt qu'à la résolution différée du sort
        // (resolveCast) — trop tard, le client continue sinon d'interpoler le
        // déplacement pendant toute la durée de l'incantation.
        movementEngine.stopMovement(caster);
        caster.updateCast(
                new ActiveCast(activeSkill, target, System.nanoTime(), activeSkill.castingTimeMs() * 1_000_000L));
        casting.put(caster.getId(), caster);
        log.debug("activeSkill.cast_started thread={} caster={} activeSkill={} castingTimeMs={}",
                Thread.currentThread().getName(), caster.getId(), activeSkill.name(), activeSkill.castingTimeMs());
        caster.broadcast(new SkillCastStarted(caster.getId(), caster.getName(), activeSkill.id(), activeSkill.name(),
                target.getId(), target.getName(), activeSkill.castingTimeMs()), null);
    }

    public void cancelCast(AbstractCharacter caster) {
        ActiveCast activeCast = caster.getActiveCast();
        if (activeCast == null) {
            return;
        }
        caster.clearCast();
        casting.remove(caster.getId());
        log.debug("activeSkill.cast_cancelled thread={} caster={}", Thread.currentThread().getName(), caster.getId());
        caster.broadcast(new SkillCastCancelled(caster.getId(), caster.getName(), activeCast.activeSkill().id(),
                activeCast.activeSkill().name()), null);
    }

    @Scheduled(fixedRate = TICK_INTERVAL_MS)
    void tick() {
        long now = System.nanoTime();
        for (AbstractCharacter caster : casting.values()) {
            ActiveCast activeCast = caster.getActiveCast();
            if (activeCast == null) {
                casting.remove(caster.getId());
                continue;
            }
            if (now - activeCast.startedAtNanos() < activeCast.castingTimeNanos()) {
                continue;
            }
            casting.remove(caster.getId());
            caster.clearCast();
            try {
                resolveCast(caster, activeCast);
            } catch (Exception e) {
                log.error("activeSkill.cast_resolution_failed caster={}", caster.getId(), e);
            }
        }
    }

    private void resolveCast(AbstractCharacter caster, ActiveCast activeCast) {
        ActiveSkill activeSkill = activeCast.activeSkill();
        AbstractCharacter target = activeCast.target();

        if (caster.getCurrentHealth() <= 0) {
            return;
        }
        if (!isTargetStillValid(caster, activeSkill, target)) {
            caster.send(new SkillFizzled(activeSkill.id(), activeSkill.name(), "La cible n'est plus valide."));
            return;
        }
        if (!caster.trySpendMana(activeSkill.manaCost())) {
            caster.send(new SkillFizzled(activeSkill.id(), activeSkill.name(), "Plus assez de mana."));
            return;
        }

        if (activeSkill.effect() == SkillEffectType.DAMAGE && activeSkill.projectile()) {
            SkillSystem.AttackRollOutcome roll = caster.getSkillSystem().rollDamage(activeSkill, target);
            caster.getSkillSystem().markCooldown(activeSkill);
            DomainEventPublisher
                    .publish(new SkillCast(caster, activeSkill, target, roll.amount(), false, roll.hit(), null));
            projectileEngine.launch(caster, activeSkill, target, roll);
            return;
        }

        SkillSystem.CastOutcome outcome = caster.getSkillSystem().cast(activeSkill, target);
        DomainEventPublisher.publish(new SkillCast(caster, activeSkill, target, outcome.amount(),
                outcome.targetDefeated(), outcome.hit(), outcome.effectExpiresAt()));

        if (activeSkill.effect() == SkillEffectType.BUFF || activeSkill.effect() == SkillEffectType.DEBUFF) {
            boolean beneficial = activeSkill.effect() == SkillEffectType.BUFF;
            caster.broadcast(new SkillModifierAnnounced(caster.getId(), caster.getName(), activeSkill.id(),
                    activeSkill.name(), target.getId(), target.getName(), target == caster, beneficial, outcome.hit(),
                    activeSkill.modifiedStat(), outcome.amount(), activeSkill.durationSeconds(), activeSkill.manaCost(),
                    caster.getCurrentMana(), caster.getMaxMana()), null);
            return;
        }

        caster.send(new CastResult(activeSkill.id(), activeSkill.name(), target.getId(), target.getName(),
                outcome.selfHeal(), outcome.hit(), outcome.amount(), outcome.targetHealthAfter(),
                outcome.targetMaxHealth(), outcome.targetDefeated(), activeSkill.manaCost(), caster.getCurrentMana(),
                caster.getMaxMana()));
        caster.broadcast(
                new SkillCastAnnounced(caster.getId(), caster.getName(), activeSkill.id(), activeSkill.name(),
                        target.getId(), target.getName(), outcome.selfHeal(), outcome.hit(), outcome.amount(),
                        outcome.targetHealthAfter(), outcome.targetMaxHealth(), outcome.targetDefeated()),
                caster instanceof CharacterInstance player ? player : null);
        if (outcome.targetDefeated()) {
            caster.clearCombatTarget();
        }
    }

    private boolean isTargetStillValid(AbstractCharacter caster, ActiveSkill activeSkill, AbstractCharacter target) {
        if (target == caster) {
            return true;
        }
        if (target.getCurrentHealth() <= 0 || !caster.getCurrentMap().isPresent(target)) {
            return false;
        }
        return activeSkill.range() <= 0 || caster.getPosition().distanceTo(target.getPosition()) <= activeSkill.range();
    }

    public record ActiveCast(ActiveSkill activeSkill, AbstractCharacter target, long startedAtNanos,
            long castingTimeNanos) {
    }
}
