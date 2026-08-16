package fr.idev.mudserver.persistence.listener;

import java.util.Map;

import fr.idev.mudserver.domain.actor.system.CombatSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

import fr.idev.mudserver.domain.actor.instance.CharacterInstance;
import fr.idev.mudserver.domain.actor.event.CharacterDied;
import fr.idev.mudserver.domain.actor.event.CharacterGainedXp;
import fr.idev.mudserver.domain.actor.event.CharacterLeveledUp;
import fr.idev.mudserver.domain.actor.event.CharacterReceivedGold;
import fr.idev.mudserver.domain.actor.event.CharacterSpentGold;
import fr.idev.mudserver.domain.actor.event.GamePlayerDied;
import fr.idev.mudserver.domain.actor.event.GamePlayerUsedPotion;
import fr.idev.mudserver.domain.actor.event.LongRestTaken;
import fr.idev.mudserver.domain.actor.event.NewGamePlayerCreated;
import fr.idev.mudserver.domain.actor.event.ShortRestTaken;
import fr.idev.mudserver.domain.actor.system.LevelingSystem;
import fr.idev.mudserver.domain.world.RoomInstance;
import fr.idev.mudserver.game.catalog.LevelCatalog;
import fr.idev.mudserver.network.message.ingame.GoldLooted;
import fr.idev.mudserver.network.message.ingame.GoldSpent;
import fr.idev.mudserver.network.message.ingame.HpRestored;
import fr.idev.mudserver.network.message.ingame.ItemUsed;
import fr.idev.mudserver.network.message.ingame.LongRestAnnounced;
import fr.idev.mudserver.network.message.ingame.PlayerLeveledUp;
import fr.idev.mudserver.network.message.ingame.PlayerRespawned;
import fr.idev.mudserver.network.message.ingame.ShortRestAnnounced;
import fr.idev.mudserver.network.message.ingame.XpGained;
import fr.idev.mudserver.persistence.CharacterDao;

@Service
public class CharacterPersistenceListener {

    private static final Logger log = LoggerFactory.getLogger(CharacterPersistenceListener.class);

    private final CharacterDao characterDao;
    private final LevelCatalog levelCatalog;

    public CharacterPersistenceListener(CharacterDao characterDao, LevelCatalog levelCatalog) {
        this.characterDao = characterDao;
        this.levelCatalog = levelCatalog;
    }

    @EventListener
    void onNewGamePlayerCreated(NewGamePlayerCreated event) {
        characterDao.insert(event.character());
        log.info("character.created character={} accountId={} race={} class={}", event.character().getName(),
                event.character().getAccountId(), event.character().getRace(), event.character().getCharacterClass());
    }

    @EventListener
    void onCharacterGainedXp(CharacterGainedXp event) {
        CharacterInstance character = event.character();
        character.send(new XpGained(event.amount()));

        while (character.getLevel() < levelCatalog.maxLevel()
                && character.getXp() >= levelCatalog.xpRequiredForLevel(character.getLevel() + 1)) {
            LevelingSystem.applyLevelUp(character);
        }

        characterDao.update(character);
        log.info("character.xp_gained character={} amount={} newXp={} newLevel={}", character.getName(), event.amount(),
                character.getXp(), character.getLevel());
    }

    @EventListener
    void onCharacterLeveledUp(CharacterLeveledUp event) {
        CharacterInstance character = event.character();
        character.getCurrentRoom().broadcast(new PlayerLeveledUp(character.getName(), event.newLevel()), null);
        log.info("character.leveled_up character={} newLevel={} hpGained={}", character.getName(), event.newLevel(),
                event.hpGained());
    }

    @EventListener
    void onCharacterReceivedGold(CharacterReceivedGold event) {
        characterDao.update(event.character());
        event.character().send(new GoldLooted(event.amount()));
        log.info("character.gold_received character={} amount={} newGold={}", event.character().getName(),
                event.amount(), event.character().getGold());
    }

    @EventListener
    void onCharacterSpentGold(CharacterSpentGold event) {
        characterDao.update(event.character());
        event.character().send(new GoldSpent(event.amount()));
        log.info("character.gold_spent character={} amount={} newGold={}", event.character().getName(), event.amount(),
                event.character().getGold());
    }

    @EventListener
    @Order(2)
    void onCharacterDied(CharacterDied event) {
        CharacterInstance killer = event.killer();
        int xpReward = event.character().getTemplate().getXpReward();
        LevelingSystem.gainXp(killer, xpReward);
        CombatSystem.setTarget(null, killer);
        log.info("combat.kill_credited killer={} monster={} xpReward={}", killer.getName(), event.character().getName(),
                xpReward);
    }

    @EventListener
    @Order(2)
    void onGamePlayerDied(GamePlayerDied event) {
        CharacterInstance character = event.character();
        RoomInstance startingRoom = character.getWorldInstance().startingRoomInstance()
                .orElseThrow(() -> new IllegalStateException("Aucune starting room configurée"));

        character.setCurrentHealth(character.getMaxHealth());
        character.moveToRoom(startingRoom);
        characterDao.update(character);

        character.send(new PlayerRespawned(startingRoom.getName()));
        log.info("character.respawned character={} room={}", character.getName(), startingRoom.getName());
    }

    @EventListener
    void onGamePlayerUsedPotion(GamePlayerUsedPotion event) {
        CharacterInstance character = event.character();
        characterDao.update(character);
        character.send(new ItemUsed(event.item().getName(), event.item().getRarity(), event.healedAmount(),
                character.getCurrentHealth(), character.getMaxHealth()));
        log.info("character.used_potion character={} item={} healedAmount={}", character.getName(),
                event.item().getName(), event.healedAmount());
    }

    @EventListener
    void onShortRestTaken(ShortRestTaken event) {
        for (Map.Entry<CharacterInstance, Integer> entry : event.healedAmounts().entrySet()) {
            CharacterInstance character = entry.getKey();
            characterDao.update(character);
            character.send(new HpRestored(entry.getValue(), character.getCurrentHealth(), character.getMaxHealth()));
        }
        event.initiator().getWorldInstance().broadcast(new ShortRestAnnounced(event.initiator().getName()), null);
        log.info("character.short_rest_taken initiator={} affected={}", event.initiator().getName(),
                event.healedAmounts().size());
    }

    @EventListener
    void onLongRestTaken(LongRestTaken event) {
        for (Map.Entry<CharacterInstance, Integer> entry : event.healedAmounts().entrySet()) {
            CharacterInstance character = entry.getKey();
            characterDao.update(character);
            character.send(new HpRestored(entry.getValue(), character.getCurrentHealth(), character.getMaxHealth()));
        }
        event.initiator().getWorldInstance().broadcast(new LongRestAnnounced(event.initiator().getName()), null);
        log.info("character.long_rest_taken initiator={} affected={} provisionsConsumed={}",
                event.initiator().getName(), event.healedAmounts().size(), event.consumedFood().size());
    }

}
