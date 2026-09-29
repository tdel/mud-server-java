package app.game.engine;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import app.domain.actor.system.SkillSystem.LearnResult;
import app.domain.actor.event.CharacterLearnedPassiveSkill;
import app.domain.actor.event.CharacterLearnedSkill;
import app.domain.actor.event.CharacterLeveledUp;
import app.domain.actor.event.DomainEventPublisher;
import app.domain.actor.event.NewGamePlayerCreated;
import app.domain.actor.instance.PlayerInstance;
import app.game.SkillTrainer;
import app.game.catalog.PassiveSkillCatalog;
import app.game.catalog.PassiveSkillCatalogHolder;
import app.game.catalog.SkillCatalog;
import app.game.catalog.SkillCatalogHolder;
import app.network.message.ingame.NewSkillsAvailable;

@Component
public class SkillLearningEngine {

    private static final Logger log = LoggerFactory.getLogger(SkillLearningEngine.class);

    private final SkillTrainer skillTrainer;

    public SkillLearningEngine(SkillTrainer skillTrainer) {
        this.skillTrainer = skillTrainer;
    }

    @EventListener
    void onNewGamePlayerCreated(NewGamePlayerCreated event) {
        learnSkillsAt(event.character(), event.character().getLevelingSystem().getLevel());
        learnPassiveSkillsAt(event.character(), event.character().getLevelingSystem().getLevel());
    }

    @EventListener
    void onCharacterLeveledUp(CharacterLeveledUp event) {
        learnSkillsAt(event.character(), event.newLevel());
        learnPassiveSkillsAt(event.character(), event.newLevel());
        // Seuls les levels autoGet s'apprennent d'office : on signale au joueur
        // qu'un nouveau palier l'attend chez le maître des compétences.
        List<SkillTrainer.Offer> offers = skillTrainer.offers(event.character());
        boolean newPalier = offers.stream()
                .anyMatch(offer -> offer.available() && offer.requiredLevel() == event.newLevel());
        if (newPalier) {
            long count = offers.stream().filter(SkillTrainer.Offer::available).count();
            event.character().send(new NewSkillsAvailable(event.newLevel(), (int) count));
        }
    }

    private void learnSkillsAt(PlayerInstance character, int level) {
        for (SkillCatalog.LearnableSkill entry : SkillCatalogHolder
                .skillsLearnableAt(character.getClassSystem().getCharacterClass(), level)) {
            int previousLevel = character.getSkillSystem().levelOf(entry.skill().id());
            LearnResult result = character.getSkillSystem().learn(entry.skill(), entry.level());
            if (result == LearnResult.NEW || result == LearnResult.UPGRADED) {
                DomainEventPublisher.publish(new CharacterLearnedSkill(character, entry.skill(), entry.level(),
                        result == LearnResult.UPGRADED ? previousLevel : 0));
                log.info("character.skill_autolearned character={} activeSkill={} level={} characterLevel={} result={}",
                        character.getName(), entry.skill().name(), entry.level(), level, result);
            }
        }
    }

    private void learnPassiveSkillsAt(PlayerInstance character, int level) {
        for (PassiveSkillCatalog.LearnablePassiveSkill entry : PassiveSkillCatalogHolder
                .passiveSkillsLearnableAt(character.getClassSystem().getCharacterClass(), level)) {
            int previousLevel = character.getSkillSystem().passiveLevelOf(entry.passiveSkill().id());
            boolean learned = character.getSkillSystem().learn(entry.passiveSkill(), entry.level());
            if (learned) {
                DomainEventPublisher.publish(new CharacterLearnedPassiveSkill(character, entry.passiveSkill(),
                        entry.level(), previousLevel));
                log.info("character.passive_skill_autolearned character={} passiveSkill={} level={} characterLevel={}",
                        character.getName(), entry.passiveSkill().name(), entry.level(), level);
            }
        }
    }

    public void reconcile(PlayerInstance character) {
        for (int level = 1; level <= character.getLevelingSystem().getLevel(); level++) {
            learnSkillsAt(character, level);
            learnPassiveSkillsAt(character, level);
        }
    }
}
