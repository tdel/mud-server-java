package app.game.engine;

import java.time.Instant;

import app.domain.ActiveEffect;
import app.domain.ActiveSkill;
import app.domain.SkillEffectDefinition;
import app.domain.SkillEffectType;
import app.domain.actor.AbstractCharacter;
import app.domain.actor.event.CharacterEffectExpired;
import app.domain.actor.event.DomainEventPublisher;

// Effets secondaires (effects[]) d'un skill DAMAGE/HEALING, partagé entre
// SkillCastEngine (résolution directe) et ProjectileEngine (résolution différée
// à l'impact) — pour un skill BUFF/DEBUFF "pur", l'unique entrée de effects()
// EST déjà l'effet principal, géré dans SkillSystem.castModifier : ne pas la
// réappliquer ici.
final class SkillEffectApplier {

    private SkillEffectApplier() {
    }

    static void applySecondaryEffects(ActiveSkill activeSkill, AbstractCharacter target) {
        if (activeSkill.skillType() == SkillEffectType.BUFF || activeSkill.skillType() == SkillEffectType.DEBUFF) {
            return;
        }
        for (SkillEffectDefinition definition : activeSkill.effects()) {
            target.getEffectsSystem()
                    .apply(new ActiveEffect(activeSkill.id(), activeSkill.name(), definition.effect(),
                            Instant.now().plusSeconds(definition.time())))
                    .ifPresent(effect -> DomainEventPublisher.publish(new CharacterEffectExpired(target, effect)));
        }
    }
}
