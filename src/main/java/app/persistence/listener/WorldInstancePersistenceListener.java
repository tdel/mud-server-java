package app.persistence.listener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import app.domain.actor.event.GamePlayerMovedToMap;
import app.domain.actor.event.GamePlayerSpawnedToMap;
import app.domain.map.Position;
import app.persistence.CharacterDao;

@Service
public class WorldInstancePersistenceListener {

    private static final Logger log = LoggerFactory.getLogger(WorldInstancePersistenceListener.class);

    private final CharacterDao characterDao;

    public WorldInstancePersistenceListener(CharacterDao characterDao) {
        this.characterDao = characterDao;
    }

    @EventListener
    void onGamePlayerMovedToMap(GamePlayerMovedToMap event) {
        characterDao.updateCurrentMap(event.character().getId(), event.to().getTemplateId());
        log.debug("map.player_moved character={} to={}", event.character().getName(), event.to().getName());
    }

    @EventListener
    void onGamePlayerSpawnedToMap(GamePlayerSpawnedToMap event) {
        characterDao.updateCurrentMap(event.character().getId(), event.map().getTemplateId());
        Position position = event.character().getMotionSystem().getPosition();
        if (position != null) {
            characterDao.updatePosition(event.character().getId(), position.x(), position.y());
        }
        log.info("map.player_spawned character={} map={}", event.character().getName(), event.map().getName());
    }
}
