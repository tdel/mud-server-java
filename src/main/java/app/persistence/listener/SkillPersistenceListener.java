package app.persistence.listener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import app.domain.actor.event.CharacterLearnedSkill;
import app.domain.actor.event.SkillCast;
import app.domain.actor.instance.CharacterInstance;
import app.network.message.ingame.SkillLearned;
import app.persistence.CharacterDao;
import app.persistence.CharacterSkillDao;

@Service
public class SkillPersistenceListener {

    private static final Logger log = LoggerFactory.getLogger(SkillPersistenceListener.class);

    private final CharacterSkillDao characterSkillDao;
    private final CharacterDao characterDao;

    public SkillPersistenceListener(CharacterSkillDao characterSkillDao, CharacterDao characterDao) {
        this.characterSkillDao = characterSkillDao;
        this.characterDao = characterDao;
    }

    @EventListener
    @Transactional
    void onCharacterLearnedSkill(CharacterLearnedSkill event) {
        if (event.previousTier() != null) {
            characterSkillDao.deleteByCharacterAndSkill(event.character().getId(), event.previousTier().id());
        }
        characterSkillDao.insert(event.character().getId(), event.activeSkill().id());
        event.character().send(
                new SkillLearned(event.activeSkill().name(), event.activeSkill().tier(), event.previousTier() != null));
        log.info("character.learned_skill character={} activeSkill={} tier={} upgraded={}", event.character().getName(),
                event.activeSkill().name(), event.activeSkill().tier(), event.previousTier() != null);
    }

    @EventListener
    void onSkillCast(SkillCast event) {
        if (event.caster() instanceof CharacterInstance caster) {
            characterDao.update(caster);
        }
        log.info("activeSkill.cast caster={} activeSkill={} amount={} targetDefeated={}", event.caster().getName(),
                event.activeSkill().name(), event.amount(), event.targetDefeated());
    }
}
