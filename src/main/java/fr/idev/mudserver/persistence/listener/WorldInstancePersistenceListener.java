package fr.idev.mudserver.persistence.listener;

import fr.idev.mudserver.domain.actor.component.IdentityComponent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import fr.idev.mudserver.domain.actor.event.GamePlayerMovedToRoom;
import fr.idev.mudserver.domain.actor.event.GamePlayerSpawnedToRoom;
import fr.idev.mudserver.domain.actor.event.WorldInstanceCreated;
import fr.idev.mudserver.persistence.CharacterDao;
import fr.idev.mudserver.persistence.WorldInstanceDao;

@Service
public class WorldInstancePersistenceListener {

    private static final Logger log = LoggerFactory.getLogger(WorldInstancePersistenceListener.class);

    private final WorldInstanceDao worldInstanceDao;
    private final CharacterDao characterDao;

    public WorldInstancePersistenceListener(WorldInstanceDao worldInstanceDao, CharacterDao characterDao) {
        this.worldInstanceDao = worldInstanceDao;
        this.characterDao = characterDao;
    }

    @EventListener
    void onWorldInstanceCreated(WorldInstanceCreated event) {
        worldInstanceDao.insert(event.instance());
    }

    @EventListener
    void onGamePlayerMovedToRoom(GamePlayerMovedToRoom event) {
        characterDao.updateCurrentRoom(event.character().getId(), event.to().getTemplateId());
        log.debug("room.player_moved character={} to={}", event.character().component(IdentityComponent.class).name(),
                event.to().getName());
    }

    @EventListener
    void onGamePlayerSpawnedToRoom(GamePlayerSpawnedToRoom event) {
        characterDao.updateCurrentRoom(event.character().getId(), event.room().getTemplateId());
        log.info("room.player_spawned character={} room={}",
                event.character().component(IdentityComponent.class).name(), event.room().getName());
    }
}
