package fr.idev.mudserver.game.engine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import fr.idev.mudserver.domain.Spell;
import fr.idev.mudserver.domain.actor.event.CharacterLearnedSpell;
import fr.idev.mudserver.domain.actor.event.CharacterLeveledUp;
import fr.idev.mudserver.domain.actor.event.DomainEventPublisher;
import fr.idev.mudserver.domain.actor.event.NewGamePlayerCreated;
import fr.idev.mudserver.domain.actor.instance.CharacterInstance;
import fr.idev.mudserver.game.catalog.SpellCatalogHolder;

@Component
public class SpellLearningEngine {

    private static final Logger log = LoggerFactory.getLogger(SpellLearningEngine.class);

    @EventListener
    void onNewGamePlayerCreated(NewGamePlayerCreated event) {
        learnSpellsAt(event.character(), event.character().getLevel());
    }

    @EventListener
    void onCharacterLeveledUp(CharacterLeveledUp event) {
        learnSpellsAt(event.character(), event.newLevel());
    }

    private void learnSpellsAt(CharacterInstance character, int level) {
        for (Spell spell : SpellCatalogHolder.spellsLearnableAt(character.getCharacterClass(), level)) {
            if (character.getSpellCasting().learn(spell.id())) {
                DomainEventPublisher.publish(new CharacterLearnedSpell(character, spell));
                log.info("character.spell_autolearned character={} spell={} level={}", character.getName(),
                        spell.name(), level);
            }
        }
    }
}
