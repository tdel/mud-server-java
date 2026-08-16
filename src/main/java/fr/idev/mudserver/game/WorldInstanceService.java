package fr.idev.mudserver.game;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

import fr.idev.mudserver.domain.Account;
import fr.idev.mudserver.domain.world.RoomInstance;
import fr.idev.mudserver.domain.world.RoomTemplate;
import fr.idev.mudserver.domain.world.WorldInstance;
import fr.idev.mudserver.domain.world.WorldTemplate;
import fr.idev.mudserver.domain.actor.instance.CharacterInstance;
import fr.idev.mudserver.domain.actor.event.CharacterDied;
import fr.idev.mudserver.domain.actor.event.DomainEventPublisher;
import fr.idev.mudserver.domain.actor.event.GamePlayerDied;
import fr.idev.mudserver.domain.actor.event.WorldInstanceCreated;
import fr.idev.mudserver.game.catalog.MonsterCatalog;
import fr.idev.mudserver.game.catalog.NpcCatalog;
import fr.idev.mudserver.game.catalog.WorldTemplateCatalog;
import fr.idev.mudserver.network.Connection;
import fr.idev.mudserver.network.ConnectionState;
import fr.idev.mudserver.network.message.ingame.GamePlayerDefeated;
import fr.idev.mudserver.network.message.ingame.MonsterDefeated;
import fr.idev.mudserver.persistence.AccountDao;
import fr.idev.mudserver.persistence.CharacterDao;
import fr.idev.mudserver.persistence.WorldInstanceDao;
import fr.idev.mudserver.persistence.listener.ItemPersistenceListener;

// La persistance de WorldInstanceCreated/GamePlayerMovedToRoom/GamePlayerSpawnedToRoom vit
// dans persistence.listener.WorldInstancePersistenceListener — ici ne reste que
// l'orchestration (matérialisation, entrée/sortie de partie, nettoyage de room).
@Service
public class WorldInstanceService {

    private static final Logger log = LoggerFactory.getLogger(WorldInstanceService.class);

    private final Map<UUID, WorldInstance> residentInstances = new ConcurrentHashMap<>();

    private final WorldTemplateCatalog worldTemplateService;
    private final WorldInstanceDao worldInstanceDao;
    private final MonsterCatalog monsterService;
    private final NpcCatalog npcService;
    private final ItemPersistenceListener itemService;
    private final AccountDao accountDao;
    private final CharacterDao characterDao;

    public WorldInstanceService(WorldTemplateCatalog worldTemplateService, WorldInstanceDao worldInstanceDao,
            MonsterCatalog monsterService, NpcCatalog npcService, ItemPersistenceListener itemService,
            AccountDao accountDao, CharacterDao characterDao) {
        this.worldTemplateService = worldTemplateService;
        this.worldInstanceDao = worldInstanceDao;
        this.monsterService = monsterService;
        this.npcService = npcService;
        this.itemService = itemService;
        this.accountDao = accountDao;
        this.characterDao = characterDao;
    }

    public WorldInstance getOrMaterialize(UUID worldInstanceId) {
        WorldInstance resident = residentInstances.get(worldInstanceId);
        if (resident != null) {
            return resident;
        }

        WorldInstance instance = worldInstanceDao.findById(worldInstanceId)
                .orElseThrow(() -> new IllegalStateException("WorldInstance " + worldInstanceId + " absente en base"));
        return materialize(instance);
    }

    public WorldInstance materialize(WorldInstance instance) {
        WorldInstance resident = residentInstances.get(instance.getId());
        if (resident != null) {
            return resident;
        }

        WorldTemplate template = worldTemplateService.findById(instance.getWorldTemplateId())
                .orElseThrow(() -> new IllegalStateException("WorldTemplate " + instance.getWorldTemplateId()
                        + " absent, requis par WorldInstance " + instance.getId()));

        Map<UUID, RoomInstance> roomInstances = new LinkedHashMap<>();
        for (RoomTemplate roomTemplate : template.getRoomTemplates().values()) {
            UUID roomInstanceId = RoomInstance.deterministicId(instance.getId(), roomTemplate.getId());
            roomInstances.put(roomTemplate.getId(), new RoomInstance(roomInstanceId, roomTemplate, instance));
        }

        long placementStart = System.currentTimeMillis();
        monsterService.placeMonsters(roomInstances.values());
        npcService.warmNpcs(List.of(template), roomInstances.values());
        long placementDurationMs = System.currentTimeMillis() - placementStart;

        instance.setRoomInstances(roomInstances);
        residentInstances.put(instance.getId(), instance);
        log.info("world_instance.materialized id={} worldTemplateId={} rooms={} placementDurationMs={}",
                instance.getId(), instance.getWorldTemplateId(), roomInstances.size(), placementDurationMs);
        return instance;
    }

    public WorldInstance createInstance(UUID worldTemplateId, Set<UUID> memberAccountIds, UUID leaderAccountId) {
        WorldInstance instance = new WorldInstance(UUID.randomUUID(), worldTemplateId, Instant.now(), leaderAccountId,
                memberAccountIds);
        materialize(instance);
        DomainEventPublisher.publish(new WorldInstanceCreated(instance));
        return instance;
    }

    public Optional<CharacterInstance> findCharacterFor(Account account, WorldInstance instance) {
        return characterDao.findByAccountAndWorldInstance(account, instance);
    }

    public void enterGame(CharacterInstance player) {
        WorldInstance instance = player.getWorldInstance();

        player.getInventory().replaceItems(itemService.loadInventory(player));
        player.getCurrentRoom().join(player);

        instance.addPlayer(player);
        accountDao.updateCurrentCharacter(player.getAccountId(), player.getId());

        MDC.put("character", player.getName());
    }

    public void exitGame(Connection connection) {
        if (connection.state() != ConnectionState.INGAME) {
            return;
        }

        CharacterInstance character = connection.character();
        RoomInstance room = character.getCurrentRoom();
        WorldInstance instance = character.getWorldInstance();

        characterDao.update(character);
        room.disconnect(character);
        instance.removePlayer(character);
        log.info("character.session_ended character={} room={}", character.getName(), room.getName());
        MDC.remove("character");

        if (instance.onlineCharacters().isEmpty()) {
            residentInstances.remove(instance.getId());
            log.info("world_instance.evicted id={} worldTemplateId={}", instance.getId(),
                    instance.getWorldTemplateId());
        }
    }

    @EventListener
    @Order(1)
    void onCharacterDied(CharacterDied event) {
        RoomInstance room = event.character().getCurrentRoom();
        room.removeMonster(event.character());
        room.broadcast(new MonsterDefeated(event.character().getName()), null);
        log.info("combat.monster_removed_from_room monster={} room={}", event.character().getName(), room.getName());
    }

    @EventListener
    @Order(1)
    void onGamePlayerDied(GamePlayerDied event) {
        RoomInstance room = event.character().getCurrentRoom();
        room.broadcast(new GamePlayerDefeated(event.character().getName(), event.killer().getName()),
                event.character());
        log.info("combat.player_defeated character={} killer={} room={}", event.character().getName(),
                event.killer().getName(), room.getName());
    }
}
