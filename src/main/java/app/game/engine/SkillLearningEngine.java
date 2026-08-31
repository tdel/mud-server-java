package app.game.engine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import app.domain.ActiveSkill;
import app.domain.actor.system.SkillSystem.LearnResult;
import app.domain.actor.event.CharacterLearnedSkill;
import app.domain.actor.event.CharacterLeveledUp;
import app.domain.actor.event.DomainEventPublisher;
import app.domain.actor.event.NewGamePlayerCreated;
import app.domain.actor.instance.CharacterInstance;
import app.game.catalog.SkillCatalogHolder;

@Component
public class SkillLearningEngine {

    private static final Logger log = LoggerFactory.getLogger(SkillLearningEngine.class);

    @EventListener
    void onNewGamePlayerCreated(NewGamePlayerCreated event) {
        learnSkillsAt(event.character(), event.character().getLevel());
    }

    @EventListener
    void onCharacterLeveledUp(CharacterLeveledUp event) {
        learnSkillsAt(event.character(), event.newLevel());
    }

    private void learnSkillsAt(CharacterInstance character, int level) {
        for (ActiveSkill activeSkill : SkillCatalogHolder.skillsLearnableAt(character.getCharacterClass(), level)) {
            ActiveSkill previousTier = character.getSkillSystem().knownSkills().stream()
                    .filter(known -> known.name().equals(activeSkill.name())).findFirst().orElse(null);
            LearnResult result = character.getSkillSystem().learn(activeSkill);
            if (result == LearnResult.NEW || result == LearnResult.UPGRADED) {
                DomainEventPublisher.publish(new CharacterLearnedSkill(character, activeSkill,
                        result == LearnResult.UPGRADED ? previousTier : null));
                log.info("character.skill_autolearned character={} activeSkill={} tier={} level={} result={}",
                        character.getName(), activeSkill.name(), activeSkill.tier(), level, result);
            }
        }
    }

    public void reconcile(CharacterInstance character) {
        for (int level = 1; level <= character.getLevel(); level++) {
            learnSkillsAt(character, level);
        }
    }
}
