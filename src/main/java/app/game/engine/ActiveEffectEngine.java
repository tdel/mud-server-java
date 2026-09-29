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

import app.domain.Party;
import app.domain.SkillEffectType;
import app.domain.actor.AbstractCharacter;
import app.domain.ActiveEffect;
import app.domain.PeriodicDamage;
import app.domain.actor.event.CharacterBeginAttack;
import app.domain.actor.event.CharacterStartedMoving;
import app.domain.actor.event.SkillCastBegin;
import app.domain.actor.event.CharacterEffectExpired;
import app.domain.actor.event.DomainEventPublisher;
import app.domain.actor.event.PlayerLoadedInWorld;
import app.domain.actor.event.SkillCast;
import app.domain.actor.instance.PlayerInstance;
import app.network.message.ingame.EffectDamage;
import app.network.message.ingame.PartyMemberEffectExpired;
import app.network.message.ingame.SkillModifierExpired;

@Component
public class ActiveEffectEngine {

    private static final Logger log = LoggerFactory.getLogger(ActiveEffectEngine.class);

    private static final long TICK_INTERVAL_MS = 1_000L;

    private final Map<UUID, AbstractCharacter> tracked = new ConcurrentHashMap<>();

    @EventListener
    void onSkillCast(SkillCast event) {
        boolean modifier = event.activeSkill().skillType() == SkillEffectType.BUFF
                || event.activeSkill().skillType() == SkillEffectType.DEBUFF;
        if (event.hit() && modifier) {
            register(event.target());
        }
    }

    @EventListener
    void onCharacterEffectExpired(CharacterEffectExpired event) {
        event.character().broadcast(new SkillModifierExpired(event.character().getName(), event.effect().skillName()),
                null);

        if (event.character() instanceof PlayerInstance character) {
            Party party = character.getPartySystem().getParty();
            if (party != null) {
                party.broadcast(new PartyMemberEffectExpired(character.getId(), character.getName(),
                        event.effect().skillName()), character);
            }
        }
    }

    @EventListener
    void onPlayerLoadedInWorld(PlayerLoadedInWorld event) {
        if (!event.character().getEffectsSystem().isEmpty()) {
            register(event.character());
        }
    }

    // Poison : chaque période échue retire son montant de PV à la cible, crédité à
    // son lanceur (XP/loot si le poison achève un monstre). Un poison ne survit
    // pas à la mort de sa cible.
    private void applyPeriodicDamage(AbstractCharacter character, Instant now) {
        if (character.getResourceSystem().getCurrentHealth() <= 0) {
            character.getEffectsSystem().removePeriodicDamage();
            return;
        }
        for (ActiveEffect effect : character.getEffectsSystem().duePeriodicDamage(now)) {
            PeriodicDamage damage = effect.periodicDamage();
            if (character.getResourceSystem().getCurrentHealth() <= 0) {
                break;
            }
            boolean defeated = character.takeDamage(damage.amount(), damage.source());
            character.broadcast(new EffectDamage(character.getId(), character.getName(), effect.skillName(),
                    damage.amount(), character.getResourceSystem().getCurrentHealth(),
                    character.getResourceSystem().getMaxHealth(), defeated, damage.source().getId()), null);
            log.debug("effect.periodic_damage character={} effect={} amount={} defeated={}", character.getId(),
                    effect.skillName(), damage.amount(), defeated);
            if (defeated) {
                character.getEffectsSystem().removePeriodicDamage();
            }
        }
    }

    public void register(AbstractCharacter character) {
        tracked.putIfAbsent(character.getId(), character);
        log.debug("effect.tracking_started character={}", character.getId());
    }

    // Relax (breakOnAction) cesse au premier geste de son porteur : déplacement,
    // attaque ou nouvelle incantation.
    @EventListener
    void onCharacterStartedMoving(CharacterStartedMoving event) {
        breakOnAction(event.character());
    }

    @EventListener
    void onCharacterBeginAttack(CharacterBeginAttack event) {
        breakOnAction(event.attacker());
    }

    @EventListener
    void onSkillCastBegin(SkillCastBegin event) {
        breakOnAction(event.caster());
    }

    private void breakOnAction(AbstractCharacter character) {
        for (ActiveEffect effect : character.getEffectsSystem().removeBreakingOnAction()) {
            log.debug("effect.broken_by_action character={} effect={}", character.getId(), effect.skillName());
            DomainEventPublisher.publish(new CharacterEffectExpired(character, effect));
        }
    }

    @Scheduled(fixedRate = TICK_INTERVAL_MS)
    void tick() {
        Instant now = Instant.now();
        for (AbstractCharacter character : tracked.values()) {
            try {
                applyPeriodicDamage(character, now);
            } catch (Exception e) {
                log.error("effect.periodic_damage_failed character={}", character.getId(), e);
            }
            List<ActiveEffect> expired = character.getEffectsSystem().expireDue(now);
            for (ActiveEffect effect : expired) {
                DomainEventPublisher.publish(new CharacterEffectExpired(character, effect));
            }
            if (character.getEffectsSystem().isEmpty()) {
                tracked.remove(character.getId());
                log.debug("effect.tracking_stopped character={}", character.getId());
            }
        }
    }
}
