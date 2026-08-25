package app.persistence.listener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import app.domain.SpellEffectType;
import app.domain.actor.component.ActiveEffect;
import app.domain.actor.event.CharacterEffectExpired;
import app.domain.actor.event.SpellCast;
import app.domain.actor.instance.CharacterInstance;
import app.persistence.CharacterActiveEffectDao;

@Service
public class ActiveEffectPersistenceListener {

    private static final Logger log = LoggerFactory.getLogger(ActiveEffectPersistenceListener.class);

    private final CharacterActiveEffectDao characterActiveEffectDao;

    public ActiveEffectPersistenceListener(CharacterActiveEffectDao characterActiveEffectDao) {
        this.characterActiveEffectDao = characterActiveEffectDao;
    }

    @EventListener
    void onSpellCast(SpellCast event) {
        boolean modifier = event.spell().effect() == SpellEffectType.BUFF
                || event.spell().effect() == SpellEffectType.DEBUFF;
        if (!event.hit() || !modifier || !(event.target() instanceof CharacterInstance targetPlayer)) {
            return;
        }
        characterActiveEffectDao.upsert(targetPlayer.getId(), new ActiveEffect(event.spell().id(), event.spell().name(),
                event.spell().modifiedStat(), event.amount(), event.expiresAt()));
        log.info("character.effect_applied character={} spell={} expiresAt={}", targetPlayer.getName(),
                event.spell().name(), event.expiresAt());
    }

    @EventListener
    void onCharacterEffectExpired(CharacterEffectExpired event) {
        if (!(event.character() instanceof CharacterInstance targetPlayer)) {
            return;
        }
        characterActiveEffectDao.delete(targetPlayer.getId(), event.effect().spellId());
        log.info("character.effect_expired character={} spell={}", targetPlayer.getName(), event.effect().spellName());
    }
}
