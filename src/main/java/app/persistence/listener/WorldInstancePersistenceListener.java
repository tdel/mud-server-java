package app.persistence.listener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import app.domain.actor.event.GamePlayerMovedToZone;
import app.domain.actor.event.GamePlayerSpawnedToZone;
import app.persistence.CharacterDao;

@Service
public class WorldInstancePersistenceListener {

    private static final Logger log = LoggerFactory.getLogger(WorldInstancePersistenceListener.class);

    private final CharacterDao characterDao;

    public WorldInstancePersistenceListener(CharacterDao characterDao) {
        this.characterDao = characterDao;
    }

    @EventListener
    void onGamePlayerMovedToZone(GamePlayerMovedToZone event) {
        characterDao.updateCurrentZone(event.character().getId(), event.to().getTemplateId());
        log.debug("zone.player_moved character={} to={}", event.character().getName(), event.to().getName());
    }

    @EventListener
    void onGamePlayerSpawnedToZone(GamePlayerSpawnedToZone event) {
        characterDao.updateCurrentZone(event.character().getId(), event.zone().getTemplateId());
        log.info("zone.player_spawned character={} zone={}", event.character().getName(), event.zone().getName());
    }
}
