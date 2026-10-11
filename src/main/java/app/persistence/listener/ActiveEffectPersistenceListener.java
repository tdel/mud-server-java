package app.persistence.listener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import app.domain.ActiveEffect;
import app.domain.actor.event.CharacterEffectApplied;
import app.domain.actor.event.CharacterEffectExpired;
import app.domain.actor.instance.PlayerInstance;
import app.domain.item.GradePenalty;
import app.persistence.CharacterActiveEffectDao;

@Service
public class ActiveEffectPersistenceListener {

    private static final Logger log = LoggerFactory.getLogger(ActiveEffectPersistenceListener.class);

    private final CharacterActiveEffectDao characterActiveEffectDao;

    public ActiveEffectPersistenceListener(CharacterActiveEffectDao characterActiveEffectDao) {
        this.characterActiveEffectDao = characterActiveEffectDao;
    }

    @EventListener
    void onCharacterEffectApplied(CharacterEffectApplied event) {
        if (!(event.character() instanceof PlayerInstance targetPlayer)) {
            return;
        }
        ActiveEffect effect = event.effect();
        // Poison (aucun modificateur, dégâts périodiques purement en mémoire) et
        // Relax (cesse au premier geste) ne survivent pas à une déconnexion ; les
        // pénalités de grade sont recalculées depuis l'équipement au chargement.
        // Altérations d'état, toggles, cubiques et soins réguliers non plus (leur
        // catégorie imposée serait aussi perdue au rechargement).
        if (effect.modifiers().isEmpty() || effect.periodicDamage() != null || effect.breaksOnAction()
                || GradePenalty.isGradePenalty(effect.skillId()) || effect.status() != null || effect.isToggle()
                || effect.cubic() != null || effect.periodicHeal() != null
                || effect.category() != ActiveEffect.categoryOf(effect.modifiers())) {
            return;
        }
        characterActiveEffectDao.upsert(targetPlayer.getId(),
                new ActiveEffect(effect.skillId(), effect.skillName(), effect.modifiers(), effect.expiresAt()));

        log.info("dao.activeeffect.CharacterEffectApplied object={} [effect={}, expiresAt={}]", targetPlayer.getId(),
                effect.skillName(), effect.expiresAt());
    }

    @EventListener
    void onCharacterEffectExpired(CharacterEffectExpired event) {
        if (!(event.character() instanceof PlayerInstance targetPlayer)) {
            return;
        }
        characterActiveEffectDao.delete(targetPlayer.getId(), event.effect().skillId());
        log.info("dao.activeeffect.CharacterEffectExpired object={} [activeSkill={}]", targetPlayer.getId(),
                event.effect().skillName());
    }
}
