package app.game.engine;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import app.domain.actor.event.CharacterPositionChanged;
import app.domain.actor.instance.PlayerInstance;
import app.domain.map.Position;
import app.persistence.CharacterDao;

@Component
public class PersistenceEngine {

    private static final Logger log = LoggerFactory.getLogger(PersistenceEngine.class);

    private static final long TICK_INTERVAL_MS = 60_000L;

    private final Map<UUID, PlayerInstance> dirtyPositions = new ConcurrentHashMap<>();
    private final CharacterDao characterDao;

    public PersistenceEngine(CharacterDao characterDao) {
        this.characterDao = characterDao;
    }

    @EventListener
    void onCharacterPositionChanged(CharacterPositionChanged event) {
        dirtyPositions.put(event.character().getId(), event.character());
    }

    @Scheduled(fixedRate = TICK_INTERVAL_MS)
    void tick() {
        for (UUID id : new ArrayList<>(dirtyPositions.keySet())) {
            PlayerInstance character = dirtyPositions.remove(id);
            if (character != null) {
                Position position = character.getMotionSystem().getPosition();
                if (position != null) {
                    characterDao.updatePosition(character.getId(), position.x(), position.y());
                    log.debug("character.position_saved character={} position={}", character.getId(), position);
                }
            }
        }
    }
}
