package app.persistence.listener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import app.domain.actor.event.CharacterLearnedSpell;
import app.domain.actor.event.SpellCast;
import app.domain.actor.instance.CharacterInstance;
import app.network.message.ingame.SpellLearned;
import app.persistence.CharacterDao;
import app.persistence.CharacterSpellDao;

@Service
public class SpellPersistenceListener {

    private static final Logger log = LoggerFactory.getLogger(SpellPersistenceListener.class);

    private final CharacterSpellDao characterSpellDao;
    private final CharacterDao characterDao;

    public SpellPersistenceListener(CharacterSpellDao characterSpellDao, CharacterDao characterDao) {
        this.characterSpellDao = characterSpellDao;
        this.characterDao = characterDao;
    }

    @EventListener
    @Transactional
    void onCharacterLearnedSpell(CharacterLearnedSpell event) {
        if (event.previousTier() != null) {
            characterSpellDao.deleteByCharacterAndSpell(event.character().getId(), event.previousTier().id());
        }
        characterSpellDao.insert(event.character().getId(), event.spell().id());
        event.character()
                .send(new SpellLearned(event.spell().name(), event.spell().tier(), event.previousTier() != null));
        log.info("character.learned_spell character={} spell={} tier={} upgraded={}", event.character().getName(),
                event.spell().name(), event.spell().tier(), event.previousTier() != null);
    }

    @EventListener
    void onSpellCast(SpellCast event) {
        characterDao.update(event.caster());
        log.info("spell.cast caster={} spell={} amount={} targetDefeated={}", event.caster().getName(),
                event.spell().name(), event.amount(), event.targetDefeated());
    }
}
