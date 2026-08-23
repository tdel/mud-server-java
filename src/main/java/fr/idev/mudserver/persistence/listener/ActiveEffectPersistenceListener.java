package fr.idev.mudserver.persistence.listener;

import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import fr.idev.mudserver.domain.SpellEffectType;
import fr.idev.mudserver.domain.actor.component.ActiveEffect;
import fr.idev.mudserver.domain.actor.event.CharacterEffectExpired;
import fr.idev.mudserver.domain.actor.event.SpellCast;
import fr.idev.mudserver.domain.actor.instance.CharacterInstance;
import fr.idev.mudserver.persistence.CharacterActiveEffectDao;

@Service
public class ActiveEffectPersistenceListener {

    private static final Logger log = LoggerFactory.getLogger(ActiveEffectPersistenceListener.class);

    private final CharacterActiveEffectDao characterActiveEffectDao;

    public ActiveEffectPersistenceListener(CharacterActiveEffectDao characterActiveEffectDao) {
        this.characterActiveEffectDao = characterActiveEffectDao;
    }

    public List<ActiveEffect> loadActiveEffects(CharacterInstance character) {
        Instant now = Instant.now();
        return characterActiveEffectDao.findByCharacterId(character.getId()).stream()
                .filter(effect -> effect.expiresAt().isAfter(now)).toList();
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
