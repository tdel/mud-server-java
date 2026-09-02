package app.game.engine;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import app.domain.ActiveSkill;
import app.domain.actor.AbstractCharacter;
import app.domain.actor.system.SkillSystem;
import app.domain.actor.instance.CharacterInstance;
import app.network.message.ingame.CastResult;
import app.network.message.ingame.SkillCastAnnounced;
import app.network.message.ingame.SkillProjectileFizzled;
import app.network.message.ingame.SkillProjectileLaunched;

/**
 * Le vol d'un projectile n'est pas simulé tick par tick (pas de collision, pas
 * d'esquive) : un seul minuteur par projectile suffit, comme pour un
 * déplacement le client anime localement la trajectoire à partir du message de
 * lancement.
 */
@Component
public class ProjectileEngine {

    private static final Logger log = LoggerFactory.getLogger(ProjectileEngine.class);

    private static final long TICK_INTERVAL_MS = 100L;

    private final Map<UUID, InFlightProjectile> projectiles = new ConcurrentHashMap<>();

    public void launch(AbstractCharacter caster, ActiveSkill activeSkill, int level, AbstractCharacter target,
            SkillSystem.AttackRollOutcome roll) {
        UUID projectileId = UUID.randomUUID();
        double distance = caster.getMotionSystem().getPosition().distanceTo(target.getMotionSystem().getPosition());
        long travelDurationMs = Math.max(1, Math.round(distance / activeSkill.projectileSpeed() * 1000));
        long impactAtNanos = System.nanoTime() + travelDurationMs * 1_000_000L;

        projectiles.put(projectileId, new InFlightProjectile(caster, activeSkill, level, target, roll, impactAtNanos));
        log.debug("projectile.launched thread={} projectileId={} caster={} activeSkill={} travelDurationMs={}",
                Thread.currentThread().getName(), projectileId, caster.getId(), activeSkill.name(), travelDurationMs);

        caster.broadcast(new SkillProjectileLaunched(projectileId, caster.getId(), caster.getName(), activeSkill.id(),
                activeSkill.name(), caster.getMotionSystem().getPosition().x(),
                caster.getMotionSystem().getPosition().y(), target.getId(), target.getName(),
                target.getMotionSystem().getPosition().x(), target.getMotionSystem().getPosition().y(),
                travelDurationMs), null);
    }

    @Scheduled(fixedRate = TICK_INTERVAL_MS)
    void tick() {
        long now = System.nanoTime();
        for (Map.Entry<UUID, InFlightProjectile> entry : projectiles.entrySet()) {
            if (now < entry.getValue().impactAtNanos()) {
                continue;
            }
            projectiles.remove(entry.getKey());
            try {
                resolveImpact(entry.getKey(), entry.getValue());
            } catch (Exception e) {
                log.error("projectile.impact_failed projectileId={}", entry.getKey(), e);
            }
        }
    }

    private void resolveImpact(UUID projectileId, InFlightProjectile projectile) {
        AbstractCharacter caster = projectile.caster();
        ActiveSkill activeSkill = projectile.activeSkill();
        AbstractCharacter target = projectile.target();

        if (target.getCurrentHealth() <= 0) {
            caster.send(new SkillProjectileFizzled(projectileId, activeSkill.id(), activeSkill.name()));
            return;
        }

        SkillSystem.CastOutcome outcome = caster.getSkillSystem().applyDamageOutcome(projectile.roll(), target);
        if (outcome.hit()) {
            SkillEffectApplier.applySecondaryEffects(activeSkill, target);
        }

        caster.send(new CastResult(activeSkill.id(), activeSkill.name(), target.getId(), target.getName(),
                outcome.selfHeal(), outcome.hit(), outcome.amount(), outcome.targetHealthAfter(),
                outcome.targetMaxHealth(), outcome.targetDefeated(), activeSkill.manaCostAt(projectile.level()),
                caster.getCurrentMana(), caster.getMaxMana()));
        caster.broadcast(
                new SkillCastAnnounced(caster.getId(), caster.getName(), activeSkill.id(), activeSkill.name(),
                        target.getId(), target.getName(), outcome.selfHeal(), outcome.hit(), outcome.amount(),
                        outcome.targetHealthAfter(), outcome.targetMaxHealth(), outcome.targetDefeated()),
                caster instanceof CharacterInstance player ? player : null);
        if (outcome.targetDefeated()) {
            caster.clearCombatTarget();
        }
    }

    public record InFlightProjectile(AbstractCharacter caster, ActiveSkill activeSkill, int level,
            AbstractCharacter target, SkillSystem.AttackRollOutcome roll, long impactAtNanos) {
    }
}
