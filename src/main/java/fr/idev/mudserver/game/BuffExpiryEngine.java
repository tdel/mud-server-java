package fr.idev.mudserver.game;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import fr.idev.mudserver.domain.actor.AbstractCharacter;
import fr.idev.mudserver.domain.actor.component.ActiveEffect;
import fr.idev.mudserver.domain.actor.event.CharacterEffectExpired;
import fr.idev.mudserver.domain.actor.event.DomainEventPublisher;

@Component
public class BuffExpiryEngine {

    private static final long TICK_INTERVAL_MS = 1_000L;

    private final Map<UUID, AbstractCharacter> tracked = new ConcurrentHashMap<>();

    public void register(AbstractCharacter character) {
        tracked.putIfAbsent(character.getId(), character);
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
            }
        }
    }
}
