package app.game.engine;

import java.time.Instant;

import org.springframework.stereotype.Component;

import app.domain.ActiveEffect;
import app.domain.ActiveSkill;
import app.domain.EffectCategory;
import app.domain.PeriodicHeal;
import app.domain.SkillEffectDefinition;
import app.domain.SkillEffectType;
import app.domain.actor.AbstractCharacter;
import app.game.Randomizer;
import app.network.message.ingame.SkillModifierAnnounced;

// Effets secondaires (effects[]) d'un skill DAMAGE/HEALING, partagé entre
// SkillCastEngine (résolution directe) et ProjectileEngine (résolution différée
// à l'impact) — pour un skill BUFF/DEBUFF "pur", l'unique entrée de effects()
// EST déjà l'effet principal, géré dans SkillSystem.castModifier : ne pas la
// réappliquer ici. Chaque effet a sa propre chance de se poser (Ice Bolt :
// ralentissement à 60 %) ; un effet à statut (Stun Attack) suit en plus le
// landRate du sort et les résistances de la cible ; un soin régulier (Greater
// Heal) reprend le hotPower du level. Un effet posé publie
// CharacterEffectApplied (suivi d'expiration, diffusion, groupe, persistance)
// et est annoncé comme un buff/debuff ordinaire.
@Component
public class SkillEffectApplier {

    void applySecondaryEffects(AbstractCharacter caster, ActiveSkill activeSkill, int level, AbstractCharacter target) {
        if (activeSkill.skillType() != SkillEffectType.DAMAGE && activeSkill.skillType() != SkillEffectType.HEALING) {
            return;
        }
        for (SkillEffectDefinition definition : activeSkill.effects()) {
            if (definition.chance() < 100 && !Randomizer.rollChance(definition.chance() / 100.0)) {
                continue;
            }
            if (definition.status() != null
                    && !caster.getSkillSystem().landsSecondaryStatus(activeSkill, target, definition)) {
                continue;
            }
            PeriodicHeal periodicHeal = definition.hotInterval() > 0
                    ? new PeriodicHeal(activeSkill.levelAt(level).hotPower(), definition.hotInterval() * 1000L)
                    : null;
            ActiveEffect effect = new ActiveEffect(activeSkill.id(), activeSkill.name(), definition.effect(),
                    Instant.now().plusSeconds(definition.time()), null, false, definition.type(), definition.status(),
                    periodicHeal, 0, null);
            caster.getSkillSystem().applyEffect(target, effect);
            caster.broadcast(
                    new SkillModifierAnnounced(caster.getId(), caster.getName(), activeSkill.id(), activeSkill.name(),
                            target.getId(), target.getName(), target == caster,
                            effect.category() == EffectCategory.BUFF, true, definition.effect(), 0, definition.time(),
                            0, caster.getResourceSystem().getCurrentMana(), caster.getResourceSystem().getMaxMana()),
                    null);
        }
    }
}
