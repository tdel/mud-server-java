package app.game.engine;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import app.domain.Party;
import app.domain.SpellEffectType;
import app.domain.actor.AbstractCharacter;
import app.domain.actor.component.ActiveEffect;
import app.domain.actor.event.CharacterEffectExpired;
import app.domain.actor.event.DomainEventPublisher;
import app.domain.actor.event.PlayerLoadedInWorld;
import app.domain.actor.event.SpellCast;
import app.domain.actor.instance.CharacterInstance;
import app.network.message.ingame.PartyMemberEffectExpired;
import app.network.message.ingame.SpellModifierExpired;

@Component
public class ActiveEffectEngine {

    private static final Logger log = LoggerFactory.getLogger(ActiveEffectEngine.class);

    private static final long TICK_INTERVAL_MS = 1_000L;

    private final Map<UUID, AbstractCharacter> tracked = new ConcurrentHashMap<>();

    @EventListener
    void onSpellCast(SpellCast event) {
        boolean modifier = event.spell().effect() == SpellEffectType.BUFF
                || event.spell().effect() == SpellEffectType.DEBUFF;
        if (event.hit() && modifier) {
            register(event.target());
        }
    }

    @EventListener
    void onCharacterEffectExpired(CharacterEffectExpired event) {
        event.character().getCurrentZone()
                .broadcast(new SpellModifierExpired(event.character().getName(), event.effect().spellName()), null);

        if (event.character() instanceof CharacterInstance character) {
            Party party = character.getParty();
            if (party != null) {
                party.broadcast(new PartyMemberEffectExpired(character.getId(), character.getName(),
                        event.effect().spellName()), character);
            }
        }
    }

    @EventListener
    void onPlayerLoadedInWorld(PlayerLoadedInWorld event) {
        if (!event.character().getActiveEffects().isEmpty()) {
            register(event.character());
        }
    }

    public void register(AbstractCharacter character) {
        tracked.putIfAbsent(character.getId(), character);
        log.debug("effect.tracking_started character={}", character.getId());
    }

    @Scheduled(fixedRate = TICK_INTERVAL_MS)
    void tick() {
        Instant now = Instant.now();
        for (AbstractCharacter character : tracked.values()) {
            List<ActiveEffect> expired = character.getActiveEffects().expireDue(now);
            for (ActiveEffect effect : expired) {
                DomainEventPublisher.publish(new CharacterEffectExpired(character, effect));
            }
            if (character.getActiveEffects().isEmpty()) {
                tracked.remove(character.getId());
                log.debug("effect.tracking_stopped character={}", character.getId());
            }
        }
    }
}
