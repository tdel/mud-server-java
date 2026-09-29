package app.game.engine;

import java.time.Instant;

import org.springframework.stereotype.Component;

import app.domain.ActiveEffect;
import app.domain.ActiveSkill;
import app.domain.EffectCategory;
import app.domain.SkillEffectDefinition;
import app.domain.SkillEffectType;
import app.domain.actor.AbstractCharacter;
import app.domain.actor.event.CharacterEffectExpired;
import app.domain.actor.event.DomainEventPublisher;
import app.game.Randomizer;
import app.network.message.ingame.SkillModifierAnnounced;

// Effets secondaires (effects[]) d'un skill DAMAGE/HEALING, partagé entre
// SkillCastEngine (résolution directe) et ProjectileEngine (résolution différée
// à l'impact) — pour un skill BUFF/DEBUFF "pur", l'unique entrée de effects()
// EST déjà l'effet principal, géré dans SkillSystem.castModifier : ne pas la
// réappliquer ici. Chaque effet a sa propre chance de se poser (Ice Bolt :
// ralentissement à 60 %) ; un effet posé est suivi par ActiveEffectEngine (pour
// son expiration) et annoncé comme un buff/debuff ordinaire.
@Component
public class SkillEffectApplier {

    private final ActiveEffectEngine activeEffectEngine;

    public SkillEffectApplier(ActiveEffectEngine activeEffectEngine) {
        this.activeEffectEngine = activeEffectEngine;
    }

    void applySecondaryEffects(AbstractCharacter caster, ActiveSkill activeSkill, AbstractCharacter target) {
        if (activeSkill.skillType() == SkillEffectType.BUFF || activeSkill.skillType() == SkillEffectType.DEBUFF) {
            return;
        }
        for (SkillEffectDefinition definition : activeSkill.effects()) {
            if (definition.chance() < 100 && !Randomizer.rollChance(definition.chance() / 100.0)) {
                continue;
            }
            ActiveEffect effect = new ActiveEffect(activeSkill.id(), activeSkill.name(), definition.effect(),
                    Instant.now().plusSeconds(definition.time()));
            target.getEffectsSystem().apply(effect)
                    .ifPresent(evicted -> DomainEventPublisher.publish(new CharacterEffectExpired(target, evicted)));
            activeEffectEngine.register(target);
            caster.broadcast(
                    new SkillModifierAnnounced(caster.getId(), caster.getName(), activeSkill.id(), activeSkill.name(),
                            target.getId(), target.getName(), target == caster,
                            effect.category() == EffectCategory.BUFF, true, definition.effect(), 0, definition.time(),
                            0, caster.getResourceSystem().getCurrentMana(), caster.getResourceSystem().getMaxMana()),
                    null);
        }
    }
}
