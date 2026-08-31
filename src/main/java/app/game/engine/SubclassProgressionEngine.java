package app.game.engine;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import app.domain.actor.Subclass;
import app.domain.actor.event.CharacterLeveledUp;
import app.domain.actor.event.DomainEventPublisher;
import app.domain.actor.event.SubclassChoiceAvailable;
import app.domain.actor.instance.CharacterInstance;

@Component
public class SubclassProgressionEngine {

    private static final Logger log = LoggerFactory.getLogger(SubclassProgressionEngine.class);

    @EventListener
    void onCharacterLeveledUp(CharacterLeveledUp event) {
        CharacterInstance character = event.character();
        Integer pendingTier = character.getPendingSubclassTier();
        if (pendingTier == null) {
            return;
        }
        List<Subclass> options = Subclass.availableAt(character.getCharacterClass(), pendingTier);
        if (!options.isEmpty()) {
            DomainEventPublisher.publish(new SubclassChoiceAvailable(character, pendingTier, options));
            log.info("character.subclass_choice_pending character={} tier={} options={}", character.getName(),
                    pendingTier, options);
        }
    }
}
