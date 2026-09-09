package app.persistence.listener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import app.domain.SkillEffectType;
import app.domain.ActiveEffect;
import app.domain.actor.event.CharacterEffectExpired;
import app.domain.actor.event.SkillCast;
import app.domain.actor.instance.PlayerInstance;
import app.persistence.CharacterActiveEffectDao;

@Service
public class ActiveEffectPersistenceListener {

    private static final Logger log = LoggerFactory.getLogger(ActiveEffectPersistenceListener.class);

    private final CharacterActiveEffectDao characterActiveEffectDao;

    public ActiveEffectPersistenceListener(CharacterActiveEffectDao characterActiveEffectDao) {
        this.characterActiveEffectDao = characterActiveEffectDao;
    }

    @EventListener
    void onSkillCast(SkillCast event) {
        boolean modifier = event.activeSkill().skillType() == SkillEffectType.BUFF
                || event.activeSkill().skillType() == SkillEffectType.DEBUFF;
        if (!event.hit() || !modifier || !(event.target() instanceof PlayerInstance targetPlayer)) {
            return;
        }
        characterActiveEffectDao.upsert(targetPlayer.getId(), new ActiveEffect(event.activeSkill().id(),
                event.activeSkill().name(), event.modifiers(), event.expiresAt()));

        log.info("character.effect_applied character={} activeSkill={} expiresAt={}", targetPlayer.getName(),
                event.activeSkill().name(), event.expiresAt());
    }

    @EventListener
    void onCharacterEffectExpired(CharacterEffectExpired event) {
        if (!(event.character() instanceof PlayerInstance targetPlayer)) {
            return;
        }
        characterActiveEffectDao.delete(targetPlayer.getId(), event.effect().skillId());
        log.info("character.effect_expired character={} activeSkill={}", targetPlayer.getName(),
                event.effect().skillName());
    }
}
