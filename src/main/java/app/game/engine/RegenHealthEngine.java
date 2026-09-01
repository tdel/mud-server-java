package app.game.engine;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import app.domain.Party;
import app.domain.actor.event.CharacterDied;
import app.domain.actor.event.GamePlayerDied;
import app.domain.actor.event.GamePlayerRespawned;
import app.domain.world.MapInstance;
import app.network.message.ingame.GamePlayerDefeated;
import app.network.message.ingame.MonsterDefeated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import app.domain.actor.event.GamePlayerDamaged;
import app.domain.actor.event.PlayerLoadedInWorld;
import app.domain.actor.instance.CharacterInstance;
import org.springframework.transaction.annotation.Transactional;

@Component
public class RegenHealthEngine {

    private static final Logger log = LoggerFactory.getLogger(RegenHealthEngine.class);

    private static final long TICK_INTERVAL_MS = 1_000L;

    private final Map<UUID, CharacterInstance> regenerating = new ConcurrentHashMap<>();

    @EventListener
    void onGamePlayerDamaged(GamePlayerDamaged event) {
        register(event.character());
    }

    @EventListener
    void onPlayerLoadedInWorld(PlayerLoadedInWorld event) {
        register(event.character());
    }

    public void register(CharacterInstance character) {
        if (isFull(character)) {
            return;
        }
        regenerating.putIfAbsent(character.getId(), character);
    }

    @Scheduled(fixedRate = TICK_INTERVAL_MS)
    void tick() {
        for (CharacterInstance character : regenerating.values()) {
            character.regenerate(character.healthRegenAmountPerTick(), 0);

            if (isFull(character)) {
                regenerating.remove(character.getId());
            }
        }
    }

    private boolean isFull(CharacterInstance character) {
        return character.getCurrentHealth() >= character.getMaxHealth();
    }

    @EventListener
    @Transactional
    void onCharacterDied(CharacterDied event) {
        MapInstance map = event.character().getMotionSystem().getCurrentMap();
        map.removeMonster(event.character());
        event.character().broadcastToMap(new MonsterDefeated(event.character().getName()), null);
        event.character().getKnownList().clear();
        log.info("regenhp.monster_removed_from_map monster={} map={}", event.character().getName(), map.getName());

        CharacterInstance killer = event.killer();
        Party party = killer.getParty();
        List<CharacterInstance> eligible = party != null
                ? party.getMembers().stream().filter(
                        member -> member.getMotionSystem().getCurrentMap() == killer.getMotionSystem().getCurrentMap())
                        .toList()
                : List.of(killer);
        double multiplier = party != null ? party.shareMultiplier(eligible.size()) : 1.0;

        event.character().grantLootTo(killer, party, eligible, multiplier);
    }

    @EventListener
    void onGamePlayerDied(GamePlayerDied event) {
        regenerating.remove(event.character().getId());

        MapInstance map = event.character().getMotionSystem().getCurrentMap();
        event.character().broadcast(new GamePlayerDefeated(event.character().getName(), event.killer().getName()),
                null);
        log.info("regenhp.player_defeated character={} killer={} map={}", event.character().getName(),
                event.killer().getName(), map.getName());
    }

    @EventListener
    void onGamePlayerRespawned(GamePlayerRespawned event) {
        register(event.character());
    }
}
