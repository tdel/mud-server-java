package fr.idev.mudserver.game.engine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import fr.idev.mudserver.domain.Spell;
import fr.idev.mudserver.domain.actor.component.SpellCasting.LearnResult;
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
            Spell previousTier = character.getSpellCasting().knownSpells().stream()
                    .filter(known -> known.name().equals(spell.name())).findFirst().orElse(null);
            LearnResult result = character.getSpellCasting().learn(spell);
            if (result == LearnResult.NEW || result == LearnResult.UPGRADED) {
                DomainEventPublisher.publish(new CharacterLearnedSpell(character, spell,
                        result == LearnResult.UPGRADED ? previousTier : null));
                log.info("character.spell_autolearned character={} spell={} tier={} level={} result={}",
                        character.getName(), spell.name(), spell.tier(), level, result);
            }
        }
    }

    public void reconcile(CharacterInstance character) {
        for (int level = 1; level <= character.getLevel(); level++) {
            learnSpellsAt(character, level);
        }
    }
}
