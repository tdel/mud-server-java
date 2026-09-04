package app.game.engine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import app.domain.actor.event.GameTimeTransitioned;
import app.domain.actor.event.DomainEventPublisher;
import app.domain.actor.event.PlayerLoadedInWorld;
import app.domain.world.DayPhase;
import app.domain.world.GameClock;
import app.domain.world.GameTime;
import app.game.WorldInstanceService;
import app.network.OutputMessage;
import app.network.message.ingame.GameTimeSync;
import app.network.message.ingame.Sunrise;
import app.network.message.ingame.Sunset;

@Component
public class TimeEngine {

    private static final Logger log = LoggerFactory.getLogger(TimeEngine.class);

    private static final long TICK_INTERVAL_MS = 1_000L;

    private final WorldInstanceService worldInstanceService;

    private volatile DayPhase lastPhase = GameClock.now().phase();

    public TimeEngine(WorldInstanceService worldInstanceService) {
        this.worldInstanceService = worldInstanceService;
    }

    @Scheduled(fixedRate = TICK_INTERVAL_MS)
    void tick() {
        GameTime current = GameClock.now();
        if (current.phase() != lastPhase) {
            lastPhase = current.phase();
            DomainEventPublisher.publish(new GameTimeTransitioned(current.phase(), current.hour(), current.minute()));
        }
    }

    @EventListener
    void onGameTimeTransitioned(GameTimeTransitioned event) {
        if (!worldInstanceService.isDefaultWorldMaterialized()) {
            return;
        }
        OutputMessage message = switch (event.phase()) {
            case DAWN -> new Sunrise(event.hour(), event.minute(), GameClock.REAL_MILLIS_PER_GAME_HOUR);
            case DUSK -> new Sunset(event.hour(), event.minute(), GameClock.REAL_MILLIS_PER_GAME_HOUR);
            default -> null;
        };
        if (message == null) {
            return;
        }
        worldInstanceService.getDefaultInstance().onlineCharacters().forEach(character -> character.send(message));
        log.info("gametime.transition phase={} hour={} minute={}", event.phase(), event.hour(), event.minute());
    }

    @EventListener
    void onPlayerLoadedInWorld(PlayerLoadedInWorld event) {
        GameTime current = GameClock.now();
        event.character().send(new GameTimeSync(current.hour(), current.minute(), current.phase()));
    }
}
