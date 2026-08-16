package fr.idev.mudserver.event;

import java.util.Map;

import fr.idev.mudserver.game.actor.LevelService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

import fr.idev.mudserver.domain.actor.Attribute;
import fr.idev.mudserver.domain.actor.instance.CharacterInstance;
import fr.idev.mudserver.domain.actor.event.CharacterDied;
import fr.idev.mudserver.domain.actor.event.CharacterGainedXp;
import fr.idev.mudserver.domain.actor.event.CharacterReceivedGold;
import fr.idev.mudserver.domain.actor.event.CharacterSpentGold;
import fr.idev.mudserver.domain.actor.event.GamePlayerDied;
import fr.idev.mudserver.domain.actor.event.GamePlayerUsedPotion;
import fr.idev.mudserver.domain.actor.event.LongRestTaken;
import fr.idev.mudserver.domain.actor.event.NewGamePlayerCreated;
import fr.idev.mudserver.domain.actor.event.ShortRestTaken;
import fr.idev.mudserver.domain.world.RoomInstance;
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
public class CharacterListener {

    private static final Logger log = LoggerFactory.getLogger(CharacterListener.class);

    private final CharacterDao characterDao;
    private final LevelService levelService;

    public CharacterListener(CharacterDao characterDao, LevelService levelService) {
        this.characterDao = characterDao;
        this.levelService = levelService;
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
        boolean leveledUp = false;

        while (character.getLevel() < levelService.maxLevel()
                && character.getXp() >= levelService.xpRequiredForLevel(character.getLevel() + 1)) {
            int hitDie = character.getCharacterClass().hitDie();
            int constitutionModifier = character.getModifier(Attribute.CONSTITUTION);
            int hpGain = Math.max(1, hitDie / 2 + 1 + constitutionModifier);

            character.setLevel(character.getLevel() + 1);
            character.setMaxHealth(character.getMaxHealth() + hpGain);
            character.setCurrentHealth(character.getCurrentHealth() + hpGain);
            leveledUp = true;
        }

        characterDao.update(character);
        log.info("character.xp_gained character={} amount={} newXp={} leveledUp={} newLevel={}", character.getName(),
                event.amount(), character.getXp(), leveledUp, character.getLevel());

        if (leveledUp) {
            character.getCurrentRoom().broadcast(new PlayerLeveledUp(character.getName(), character.getLevel()), null);
        }
    }

    @EventListener
    void onCharacterReceivedGold(CharacterReceivedGold event) {
        characterDao.update(event.character());
        event.character().send(new GoldLooted(event.amount()));
        log.info("character.gold_received character={} amount={} newGold={}", event.character().getName(),
                event.amount(), event.character().getInventory().getGold());
    }

    @EventListener
    void onCharacterSpentGold(CharacterSpentGold event) {
        characterDao.update(event.character());
        event.character().send(new GoldSpent(event.amount()));
        log.info("character.gold_spent character={} amount={} newGold={}", event.character().getName(), event.amount(),
                event.character().getInventory().getGold());
    }

    @EventListener
    @Order(2)
    void onCharacterDied(CharacterDied event) {
        CharacterInstance killer = event.killer();
        int xpReward = event.character().getTemplate().getXpReward();
        killer.gainXp(xpReward);
        killer.setTarget(null);
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
