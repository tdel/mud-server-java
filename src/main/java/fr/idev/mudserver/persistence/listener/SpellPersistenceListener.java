package fr.idev.mudserver.persistence.listener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import fr.idev.mudserver.domain.actor.event.CharacterLearnedSpell;
import fr.idev.mudserver.domain.actor.event.SpellCast;
import fr.idev.mudserver.domain.actor.instance.CharacterInstance;
import fr.idev.mudserver.network.message.ingame.SpellLearned;
import fr.idev.mudserver.persistence.CharacterDao;
import fr.idev.mudserver.persistence.CharacterSpellDao;

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
    void onCharacterLearnedSpell(CharacterLearnedSpell event) {
        characterSpellDao.insert(event.character().getId(), event.spell().id());
        event.character().send(new SpellLearned(event.spell().name()));
        log.info("character.learned_spell character={} spell={}", event.character().getName(), event.spell().name());
    }

    @EventListener
    void onSpellCast(SpellCast event) {
        characterDao.update(event.caster());
        log.info("spell.cast caster={} spell={} amount={} targetDefeated={}", event.caster().getName(),
                event.spell().name(), event.amount(), event.targetDefeated());
    }
}
