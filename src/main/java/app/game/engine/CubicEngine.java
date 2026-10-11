package app.game.engine;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import app.domain.actor.event.PlayerRemovedFromWorld;
import app.domain.AbnormalStatus;
import app.domain.ActiveEffect;
import app.domain.ActiveSkill;
import app.domain.CubicType;
import app.domain.EffectCategory;
import app.domain.SkillElement;
import app.domain.actor.AbstractCharacter;
import app.domain.actor.ModifiedStat;
import app.domain.actor.event.CharacterEffectApplied;
import app.domain.actor.instance.MonsterInstance;
import app.game.Randomizer;
import app.game.combat.CombatFormulas;
import app.network.message.ingame.SkillCastAnnounced;
import app.network.message.ingame.SkillModifierAnnounced;

// Cubiques du Warlock (Summon Storm/Binding Cubic, Mass Summon Storm Cubic) :
// un effet de 15 minutes sur leur porteur (un seul cubique de chaque type) qui
// agit seul sur la cible de combat de celui-ci — le cubique de tempête la
// foudroie toutes les 10 s, le cubique d'entrave tente de l'immobiliser toutes
// les 30 s.
@Component
public class CubicEngine {

    private static final Logger log = LoggerFactory.getLogger(CubicEngine.class);

    private static final long TICK_INTERVAL_MS = 1_000L;
    private static final int CUBIC_DURATION_SECONDS = 900;
    private static final double CUBIC_RANGE = 30.0;
    // Storm Cubic : m.atk propre du cubique (cubicPower L2J) et power du sort.
    private static final int STORM_MATK = 282;
    private static final int STORM_POWER = 25;
    private static final long STORM_PERIOD_MS = 10_000L;
    // Binding Cubic : chance d'entrave, durée.
    private static final long BINDING_PERIOD_MS = 30_000L;
    private static final double BINDING_LAND_CHANCE = 0.6;
    private static final int BINDING_SECONDS = 10;

    private final Map<UUID, AbstractCharacter> owners = new ConcurrentHashMap<>();
    private final Map<ActionKey, Long> nextActionAt = new ConcurrentHashMap<>();

    private record ActionKey(UUID ownerId, CubicType cubic) {
    }

    public static UUID effectId(CubicType cubic) {
        return UUID.nameUUIDFromBytes(("cubic:" + cubic).getBytes());
    }

    public static String label(CubicType cubic) {
        return switch (cubic) {
            case STORM -> "Storm Cubic";
            case BINDING -> "Binding Cubic";
        };
    }

    public void grant(AbstractCharacter caster, AbstractCharacter target, ActiveSkill activeSkill) {
        CubicType cubic = activeSkill.cubic();
        ActiveEffect effect = new ActiveEffect(effectId(cubic), label(cubic), List.of(),
                Instant.now().plusSeconds(CUBIC_DURATION_SECONDS), null, false, EffectCategory.BUFF, null, null, 0,
                cubic);
        caster.getSkillSystem().applyEffect(target, effect);
        log.info("cubic.granted caster={} target={} cubic={}", caster.getName(), target.getName(), cubic);
    }

    @EventListener
    void onCharacterEffectApplied(CharacterEffectApplied event) {
        if (event.effect().cubic() != null) {
            owners.put(event.character().getId(), event.character());
        }
    }

    @EventListener
    void onPlayerRemovedFromWorld(PlayerRemovedFromWorld event) {
        forget(event.character().getId());
    }

    private void forget(UUID ownerId) {
        owners.remove(ownerId);
        nextActionAt.keySet().removeIf(key -> key.ownerId().equals(ownerId));
    }

    @Scheduled(fixedRate = TICK_INTERVAL_MS)
    void tick() {
        long now = System.currentTimeMillis();
        for (AbstractCharacter owner : owners.values()) {
            List<ActiveEffect> cubics = owner.getEffectsSystem().cubics();
            if (cubics.isEmpty() || owner.getResourceSystem().getCurrentHealth() <= 0) {
                if (owners.remove(owner.getId(), owner)) {
                    nextActionAt.keySet().removeIf(key -> key.ownerId().equals(owner.getId()));
                }
                continue;
            }
            for (ActiveEffect effect : cubics) {
                try {
                    act(owner, effect.cubic(), now);
                } catch (Exception e) {
                    log.error("cubic.tick_failed owner={} cubic={}", owner.getId(), effect.cubic(), e);
                }
            }
        }
    }

    private void act(AbstractCharacter owner, CubicType cubic, long now) {
        ActionKey key = new ActionKey(owner.getId(), cubic);
        if (now < nextActionAt.getOrDefault(key, 0L)) {
            return;
        }
        AbstractCharacter target = owner.getCombatSystem().getTarget();
        if (!(target instanceof MonsterInstance monster) || monster.getResourceSystem().getCurrentHealth() <= 0
                || monster.pursuit == null
                || owner.getMotionSystem().getCurrentMap() != monster.getMotionSystem().getCurrentMap()
                || owner.getMotionSystem().getPosition()
                        .distanceTo(monster.getMotionSystem().getPosition()) > CUBIC_RANGE) {
            return;
        }
        switch (cubic) {
            case STORM -> {
                nextActionAt.put(key, now + STORM_PERIOD_MS);
                int damage = CombatFormulas.resolveMagicalDamage(STORM_MATK,
                        monster.getStatSystem().getEffective(ModifiedStat.MDEF), STORM_POWER, false, false);
                damage = CombatFormulas.applyElementalResistance(damage,
                        monster.elementalResistance(SkillElement.WIND));
                boolean defeated = monster.takeDamage(damage, owner);
                owner.broadcast(new SkillCastAnnounced(owner.getId(), owner.getName(), effectId(cubic), label(cubic),
                        monster.getId(), monster.getName(), false, true, damage,
                        monster.getResourceSystem().getCurrentHealth(), monster.getResourceSystem().getMaxHealth(),
                        defeated), null);
            }
            case BINDING -> {
                nextActionAt.put(key, now + BINDING_PERIOD_MS);
                double resist = 1 - monster.getStatSystem().adjust(ModifiedStat.RESIST_HOLD, 0) / 100.0;
                if (!Randomizer.rollChance(BINDING_LAND_CHANCE * Math.clamp(resist, 0.0, 1.0))) {
                    return;
                }
                ActiveEffect root = new ActiveEffect(effectId(cubic), label(cubic), List.of(),
                        Instant.now().plusSeconds(BINDING_SECONDS), null, false, EffectCategory.DEBUFF,
                        AbnormalStatus.ROOT, null, 0, null);
                owner.getSkillSystem().applyEffect(monster, root);
                owner.broadcast(
                        new SkillModifierAnnounced(owner.getId(), owner.getName(), effectId(cubic), label(cubic),
                                monster.getId(), monster.getName(), false, false, true, List.of(), 0, BINDING_SECONDS,
                                0, owner.getResourceSystem().getCurrentMana(), owner.getResourceSystem().getMaxMana()),
                        null);
            }
        }
    }
}
