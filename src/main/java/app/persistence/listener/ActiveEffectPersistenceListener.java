package app.persistence.listener;

import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import app.domain.Party;
import app.domain.SkillEffectType;
import app.domain.ActiveEffect;
import app.domain.actor.event.CharacterEffectExpired;
import app.domain.actor.event.SkillCast;
import app.domain.actor.instance.CharacterInstance;
import app.network.message.ingame.PartyMemberEffectApplied;
import app.persistence.CharacterActiveEffectDao;

@Service
public class ActiveEffectPersistenceListener {

    private static final Logger log = LoggerFactory.getLogger(ActiveEffectPersistenceListener.class);

    private final CharacterActiveEffectDao characterActiveEffectDao;

    public ActiveEffectPersistenceListener(CharacterActiveEffectDao characterActiveEffectDao) {
        this.characterActiveEffectDao = characterActiveEffectDao;
    }

    @EventListener
    void onSkillCast(SkillCast event) {
        boolean modifier = event.activeSkill().effect() == SkillEffectType.BUFF
                || event.activeSkill().effect() == SkillEffectType.DEBUFF;
        if (!event.hit() || !modifier || !(event.target() instanceof CharacterInstance targetPlayer)) {
            return;
        }
        characterActiveEffectDao.upsert(targetPlayer.getId(), new ActiveEffect(event.activeSkill().id(),
                event.activeSkill().name(), event.activeSkill().modifiedStat(), event.amount(), event.expiresAt()));

        Party party = targetPlayer.getParty();
        if (party != null) {
            long secondsRemaining = Duration.between(Instant.now(), event.expiresAt()).toSeconds();
            party.broadcast(new PartyMemberEffectApplied(targetPlayer.getId(), targetPlayer.getName(),
                    event.activeSkill().name(), event.activeSkill().modifiedStat().label(), event.amount(),
                    Math.max(0, secondsRemaining)), targetPlayer);
        }

        log.info("character.effect_applied character={} activeSkill={} expiresAt={}", targetPlayer.getName(),
                event.activeSkill().name(), event.expiresAt());
    }

    @EventListener
    void onCharacterEffectExpired(CharacterEffectExpired event) {
        if (!(event.character() instanceof CharacterInstance targetPlayer)) {
            return;
        }
        characterActiveEffectDao.delete(targetPlayer.getId(), event.effect().skillId());
        log.info("character.effect_expired character={} activeSkill={}", targetPlayer.getName(),
                event.effect().skillName());
    }
}
