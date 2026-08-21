package fr.idev.mudserver.persistence.listener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import fr.idev.mudserver.domain.actor.event.GamePlayerMovedToRoom;
import fr.idev.mudserver.domain.actor.event.GamePlayerSpawnedToRoom;
import fr.idev.mudserver.persistence.CharacterDao;

@Service
public class WorldInstancePersistenceListener {

    private static final Logger log = LoggerFactory.getLogger(WorldInstancePersistenceListener.class);

    private final CharacterDao characterDao;

    public WorldInstancePersistenceListener(CharacterDao characterDao) {
        this.characterDao = characterDao;
    }

    @EventListener
    void onGamePlayerMovedToRoom(GamePlayerMovedToRoom event) {
        characterDao.updateCurrentRoom(event.character().getId(), event.to().getTemplateId());
        log.debug("room.player_moved character={} to={}", event.character().getName(), event.to().getName());
    }

    @EventListener
    void onGamePlayerSpawnedToRoom(GamePlayerSpawnedToRoom event) {
        characterDao.updateCurrentRoom(event.character().getId(), event.room().getTemplateId());
        log.info("room.player_spawned character={} room={}", event.character().getName(), event.room().getName());
    }
}
