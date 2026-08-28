package app.game.engine;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import app.domain.Spell;
import app.domain.SpellEffectType;
import app.domain.actor.AbstractCharacter;
import app.domain.actor.component.SpellCasting;
import app.domain.actor.event.DomainEventPublisher;
import app.domain.actor.event.SpellCast;
import app.domain.actor.event.SpellCastBegin;
import app.domain.actor.instance.CharacterInstance;
import app.network.message.ingame.CastResult;
import app.network.message.ingame.SpellCastAnnounced;
import app.network.message.ingame.SpellCastCancelled;
import app.network.message.ingame.SpellCastStarted;
import app.network.message.ingame.SpellFizzled;
import app.network.message.ingame.SpellModifierAnnounced;

@Component
public class SpellCastEngine {

    private static final Logger log = LoggerFactory.getLogger(SpellCastEngine.class);

    private static final long TICK_INTERVAL_MS = 100L;

    private final Map<UUID, AbstractCharacter> casting = new ConcurrentHashMap<>();
    private final MovementEngine movementEngine;
    private final ProjectileEngine projectileEngine;

    public SpellCastEngine(MovementEngine movementEngine, ProjectileEngine projectileEngine) {
        this.movementEngine = movementEngine;
        this.projectileEngine = projectileEngine;
    }

    @EventListener
    void onSpellCastBegin(SpellCastBegin event) {
        beginCast(event.caster(), event.spell(), event.target());
    }

    private void beginCast(AbstractCharacter caster, Spell spell, AbstractCharacter target) {
        movementEngine.stopMovement(caster);
        caster.activeCast = new ActiveCast(spell, target, System.nanoTime(), spell.castingTimeMs() * 1_000_000L);
        casting.put(caster.getId(), caster);
        log.debug("spell.cast_started thread={} caster={} spell={} castingTimeMs={}", Thread.currentThread().getName(),
                caster.getId(), spell.name(), spell.castingTimeMs());
        caster.getCurrentZone().broadcast(new SpellCastStarted(caster.getId(), caster.getName(), spell.id(),
                spell.name(), target.getId(), target.getName(), spell.castingTimeMs()), null);
    }

    public void cancelCast(AbstractCharacter caster) {
        ActiveCast activeCast = caster.activeCast;
        if (activeCast == null) {
            return;
        }
        caster.activeCast = null;
        casting.remove(caster.getId());
        log.debug("spell.cast_cancelled thread={} caster={}", Thread.currentThread().getName(), caster.getId());
        caster.getCurrentZone().broadcast(new SpellCastCancelled(caster.getId(), caster.getName(),
                activeCast.spell().id(), activeCast.spell().name()), null);
    }

    @Scheduled(fixedRate = TICK_INTERVAL_MS)
    void tick() {
        long now = System.nanoTime();
        for (AbstractCharacter caster : casting.values()) {
            ActiveCast activeCast = caster.activeCast;
            if (activeCast == null) {
                casting.remove(caster.getId());
                continue;
            }
            if (now - activeCast.startedAtNanos() < activeCast.castingTimeNanos()) {
                continue;
            }
            casting.remove(caster.getId());
            caster.activeCast = null;
            try {
                resolveCast(caster, activeCast);
            } catch (Exception e) {
                log.error("spell.cast_resolution_failed caster={}", caster.getId(), e);
            }
        }
    }

    private void resolveCast(AbstractCharacter caster, ActiveCast activeCast) {
        Spell spell = activeCast.spell();
        AbstractCharacter target = activeCast.target();

        if (caster.getCurrentHealth() <= 0) {
            return;
        }
        if (!isTargetStillValid(caster, spell, target)) {
            caster.send(new SpellFizzled(spell.id(), spell.name(), "La cible n'est plus valide."));
            return;
        }
        if (!caster.trySpendMana(spell.manaCost())) {
            caster.send(new SpellFizzled(spell.id(), spell.name(), "Plus assez de mana."));
            return;
        }

        if (spell.effect() == SpellEffectType.DAMAGE && spell.projectile()) {
            SpellCasting.AttackRollOutcome roll = caster.getSpellCasting().rollDamage(spell, target);
            caster.getSpellCasting().markCooldown(spell);
            DomainEventPublisher.publish(new SpellCast(caster, spell, target, roll.amount(), false, roll.hit(), null));
            projectileEngine.launch(caster, spell, target, roll);
            return;
        }

        SpellCasting.CastOutcome outcome = caster.getSpellCasting().cast(spell, target);
        DomainEventPublisher.publish(new SpellCast(caster, spell, target, outcome.amount(), outcome.targetDefeated(),
                outcome.hit(), outcome.effectExpiresAt()));

        if (spell.effect() == SpellEffectType.BUFF || spell.effect() == SpellEffectType.DEBUFF) {
            boolean beneficial = spell.effect() == SpellEffectType.BUFF;
            caster.getCurrentZone()
                    .broadcast(new SpellModifierAnnounced(caster.getId(), caster.getName(), spell.id(), spell.name(),
                            target.getId(), target.getName(), target == caster, beneficial, outcome.hit(),
                            spell.modifiedStat(), outcome.amount(), spell.durationSeconds(), spell.manaCost(),
                            caster.getCurrentMana(), caster.getMaxMana()), null);
            return;
        }

        caster.send(new CastResult(spell.id(), spell.name(), target.getId(), target.getName(), outcome.selfHeal(),
                outcome.hit(), outcome.amount(), outcome.targetHealthAfter(), outcome.targetMaxHealth(),
                outcome.targetDefeated(), spell.manaCost(), caster.getCurrentMana(), caster.getMaxMana()));
        caster.getCurrentZone().broadcast(
                new SpellCastAnnounced(caster.getId(), caster.getName(), spell.id(), spell.name(), target.getId(),
                        target.getName(), outcome.selfHeal(), outcome.hit(), outcome.amount(),
                        outcome.targetHealthAfter(), outcome.targetMaxHealth(), outcome.targetDefeated()),
                caster instanceof CharacterInstance player ? player : null);
        if (outcome.targetDefeated()) {
            caster.clearCombatTarget();
        }
    }

    private boolean isTargetStillValid(AbstractCharacter caster, Spell spell, AbstractCharacter target) {
        if (target == caster) {
            return true;
        }
        if (target.getCurrentHealth() <= 0 || !caster.getCurrentZone().isPresent(target)) {
            return false;
        }
        return spell.range() <= 0 || caster.getPosition().distanceTo(target.getPosition()) <= spell.range();
    }

    public record ActiveCast(Spell spell, AbstractCharacter target, long startedAtNanos, long castingTimeNanos) {
    }
}
