package app.game.engine;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import app.domain.Spell;
import app.domain.actor.AbstractCharacter;
import app.domain.actor.component.SpellCasting;
import app.domain.actor.instance.CharacterInstance;
import app.network.message.ingame.CastResult;
import app.network.message.ingame.SpellCastAnnounced;
import app.network.message.ingame.SpellProjectileFizzled;
import app.network.message.ingame.SpellProjectileLaunched;

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

    public void launch(CharacterInstance caster, Spell spell, AbstractCharacter target,
            SpellCasting.AttackRollOutcome roll) {
        UUID projectileId = UUID.randomUUID();
        double distance = caster.getPosition().distanceTo(target.getPosition());
        long travelDurationMs = Math.max(1, Math.round(distance / spell.projectileSpeed() * 1000));
        long impactAtNanos = System.nanoTime() + travelDurationMs * 1_000_000L;

        projectiles.put(projectileId, new InFlightProjectile(caster, spell, target, roll, impactAtNanos));
        log.debug("projectile.launched thread={} projectileId={} caster={} spell={} travelDurationMs={}",
                Thread.currentThread().getName(), projectileId, caster.getId(), spell.name(), travelDurationMs);

        caster.getCurrentZone().broadcast(new SpellProjectileLaunched(projectileId, caster.getId(), caster.getName(),
                spell.id(), spell.name(), caster.getPosition().x(), caster.getPosition().y(), target.getId(),
                target.getName(), target.getPosition().x(), target.getPosition().y(), travelDurationMs), null);
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
        CharacterInstance caster = projectile.caster();
        Spell spell = projectile.spell();
        AbstractCharacter target = projectile.target();

        if (target.getCurrentHealth() <= 0) {
            caster.send(new SpellProjectileFizzled(projectileId, spell.id(), spell.name()));
            return;
        }

        SpellCasting.CastOutcome outcome = caster.getSpellCasting().applyDamageOutcome(projectile.roll(), target);

        caster.send(new CastResult(spell.id(), spell.name(), target.getId(), target.getName(), outcome.selfHeal(),
                outcome.hit(), outcome.amount(), outcome.targetHealthAfter(), outcome.targetMaxHealth(),
                outcome.targetDefeated(), spell.manaCost(), caster.getCurrentMana(), caster.getMaxMana()));
        caster.getCurrentZone().broadcast(new SpellCastAnnounced(caster.getId(), caster.getName(), spell.id(),
                spell.name(), target.getId(), target.getName(), outcome.selfHeal(), outcome.hit(), outcome.amount(),
                outcome.targetHealthAfter(), outcome.targetMaxHealth(), outcome.targetDefeated()), caster);
        if (outcome.targetDefeated()) {
            caster.getCombat().setTarget(null);
        }
    }

    public record InFlightProjectile(CharacterInstance caster, Spell spell, AbstractCharacter target,
            SpellCasting.AttackRollOutcome roll, long impactAtNanos) {
    }
}
