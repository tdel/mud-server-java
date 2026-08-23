package fr.idev.mudserver.game;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

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
import fr.idev.mudserver.domain.world.WorldTemplateSummary;
import fr.idev.mudserver.domain.actor.instance.CharacterInstance;
import fr.idev.mudserver.domain.actor.event.CharacterDied;
import fr.idev.mudserver.domain.actor.event.GamePlayerDied;
import fr.idev.mudserver.game.catalog.MonsterCatalog;
import fr.idev.mudserver.game.catalog.NpcCatalog;
import fr.idev.mudserver.game.catalog.WorldTemplateCatalog;
import fr.idev.mudserver.network.Connection;
import fr.idev.mudserver.network.ConnectionState;
import fr.idev.mudserver.network.message.ingame.GamePlayerDefeated;
import fr.idev.mudserver.network.message.ingame.MonsterDefeated;
import fr.idev.mudserver.persistence.AccountDao;
import fr.idev.mudserver.persistence.CharacterDao;
import fr.idev.mudserver.persistence.listener.ActiveEffectPersistenceListener;
import fr.idev.mudserver.persistence.listener.ItemPersistenceListener;
import fr.idev.mudserver.persistence.listener.SpellPersistenceListener;

// La persistance de GamePlayerMovedToRoom/GamePlayerSpawnedToRoom vit dans
// persistence.listener.WorldInstancePersistenceListener — ici ne reste que
// l'orchestration (matérialisation du monde par défaut, entrée/sortie de partie,
// nettoyage de room).
@Service
public class WorldInstanceService {

    private static final Logger log = LoggerFactory.getLogger(WorldInstanceService.class);

    private final WorldTemplateCatalog worldTemplateService;
    private final MonsterCatalog monsterService;
    private final NpcCatalog npcService;
    private final ItemPersistenceListener itemService;
    private final SpellPersistenceListener spellService;
    private final ActiveEffectPersistenceListener activeEffectService;
    private final BuffExpiryEngine buffExpiryEngine;
    private final AccountDao accountDao;
    private final CharacterDao characterDao;

    private WorldInstance defaultInstance;

    public WorldInstanceService(WorldTemplateCatalog worldTemplateService, MonsterCatalog monsterService,
            NpcCatalog npcService, ItemPersistenceListener itemService, SpellPersistenceListener spellService,
            ActiveEffectPersistenceListener activeEffectService, BuffExpiryEngine buffExpiryEngine,
            AccountDao accountDao, CharacterDao characterDao) {
        this.worldTemplateService = worldTemplateService;
        this.monsterService = monsterService;
        this.npcService = npcService;
        this.itemService = itemService;
        this.spellService = spellService;
        this.activeEffectService = activeEffectService;
        this.buffExpiryEngine = buffExpiryEngine;
        this.accountDao = accountDao;
        this.characterDao = characterDao;
    }

    public WorldInstance materializeDefaultWorld() {
        WorldTemplateSummary summary = worldTemplateService.theOnlyTemplate();
        WorldTemplate template = worldTemplateService.findById(summary.id())
                .orElseThrow(() -> new IllegalStateException("WorldTemplate " + summary.id() + " absent"));

        WorldInstance instance = new WorldInstance(WorldInstance.DEFAULT_ID, template.getId(), Instant.now());

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
        this.defaultInstance = instance;
        log.info("world.materialized id={} worldTemplateId={} rooms={} placementDurationMs={}", instance.getId(),
                instance.getWorldTemplateId(), roomInstances.size(), placementDurationMs);
        return instance;
    }

    public WorldInstance getDefaultInstance() {
        if (defaultInstance == null) {
            throw new IllegalStateException("Le monde par défaut n'est pas encore matérialisé");
        }
        return defaultInstance;
    }

    public List<CharacterInstance> findCharactersFor(Account account) {
        return characterDao.findAllByAccount(account, getDefaultInstance());
    }

    public Optional<CharacterInstance> findCharacterByName(Account account, String name) {
        return characterDao.findByAccountAndName(account, getDefaultInstance(), name);
    }

    public void enterGame(CharacterInstance player) {
        WorldInstance instance = player.getWorldInstance();

        player.getInventory().replaceItems(itemService.loadInventory(player));
        spellService.loadLearnedSpellIds(player).forEach(player.getSpellCasting()::learn);
        activeEffectService.loadActiveEffects(player).forEach(effect -> {
            player.getActiveEffects().apply(effect);
            buffExpiryEngine.register(player);
        });
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
