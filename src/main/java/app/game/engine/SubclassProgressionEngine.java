package app.game.engine;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import app.domain.actor.Subclass;
import app.domain.actor.event.CharacterChoseSubclass;
import app.domain.actor.event.CharacterLeveledUp;
import app.domain.actor.event.DomainEventPublisher;
import app.domain.actor.event.SubclassChoiceAvailable;
import app.domain.actor.instance.PlayerInstance;
import app.game.SkillTrainer;
import app.network.message.ingame.ClassOptionView;
import app.network.message.ingame.NewSkillsAvailable;
import app.network.message.ingame.SubclassChoiceOffered;

@Component
public class SubclassProgressionEngine {

    private static final Logger log = LoggerFactory.getLogger(SubclassProgressionEngine.class);

    private final SkillTrainer skillTrainer;

    public SubclassProgressionEngine(SkillTrainer skillTrainer) {
        this.skillTrainer = skillTrainer;
    }

    // Nouvelle profession : son arbre s'ouvre aussitôt chez le Grand Master.
    @EventListener
    void onCharacterChoseSubclass(CharacterChoseSubclass event) {
        long available = skillTrainer.offers(event.character()).stream().filter(SkillTrainer.Offer::available).count();
        if (available > 0) {
            event.character()
                    .send(new NewSkillsAvailable(event.character().getLevelingSystem().getLevel(), (int) available));
        }
    }

    // Annoncé une seule fois, au level exact du palier (20, 40) : le joueur
    // change ensuite de profession quand il le souhaite auprès du Grand Master.
    @EventListener
    void onCharacterLeveledUp(CharacterLeveledUp event) {
        PlayerInstance character = event.character();
        Integer pendingTier = character.getClassSystem().getPendingSubclassTier();
        Integer requiredLevel = character.getClassSystem().nextChangeLevel();
        if (pendingTier == null || requiredLevel == null || event.newLevel() != requiredLevel) {
            return;
        }
        List<Subclass> options = character.getClassSystem().nextOptions();
        if (!options.isEmpty()) {
            character.send(new SubclassChoiceOffered(pendingTier, ClassOptionView.listOf(options)));
            DomainEventPublisher.publish(new SubclassChoiceAvailable(character, pendingTier, options));
            log.info("character.subclass_choice_pending character={} tier={} options={}", character.getName(),
                    pendingTier, options);
        }
    }
}
