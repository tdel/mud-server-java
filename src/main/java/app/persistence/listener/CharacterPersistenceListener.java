package app.persistence.listener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

import app.domain.actor.instance.CharacterInstance;
import app.domain.actor.event.CharacterDied;
import app.domain.actor.event.CharacterGainedXp;
import app.domain.actor.event.CharacterLeveledUp;
import app.domain.actor.event.CharacterReceivedGold;
import app.domain.actor.event.CharacterRegenerated;
import app.domain.actor.event.CharacterSpentGold;
import app.domain.actor.event.GamePlayerDamaged;
import app.domain.actor.event.GamePlayerDied;
import app.domain.actor.event.GamePlayerUsedManaPotion;
import app.domain.actor.event.GamePlayerUsedPotion;
import app.domain.actor.event.NewGamePlayerCreated;
import app.domain.world.ZoneInstance;
import app.game.catalog.LevelCatalog;
import app.network.message.ingame.CharacterUsedItem;
import app.network.message.ingame.GoldLooted;
import app.network.message.ingame.GoldSpent;
import app.network.message.ingame.ItemUsed;
import app.network.message.ingame.ManaPotionUsed;
import app.network.message.ingame.PlayerLeveledUp;
import app.network.message.ingame.PlayerRespawned;
import app.network.message.ingame.RegenTick;
import app.network.message.ingame.XpGained;
import app.persistence.CharacterDao;

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
    @Order(1)
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
            character.applyLevelUp();
        }

        characterDao.update(character);
        log.info("character.xp_gained character={} amount={} newXp={} newLevel={}", character.getName(), event.amount(),
                character.getXp(), character.getLevel());
    }

    @EventListener
    void onCharacterLeveledUp(CharacterLeveledUp event) {
        CharacterInstance character = event.character();
        character.getCurrentZone().broadcast(new PlayerLeveledUp(character.getName(), event.newLevel()), null);
        log.info("character.leveled_up character={} newLevel={} hpGained={}", character.getName(), event.newLevel(),
                event.hpGained());
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
        killer.getCombat().setTarget(null);
        log.info("combat.kill_credited killer={} monster={} xpReward={}", killer.getName(), event.character().getName(),
                xpReward);
    }

    @EventListener
    void onGamePlayerDamaged(GamePlayerDamaged event) {
        characterDao.update(event.character());
        log.info("combat.damage_taken character={} attacker={} amount={} currentHealth={}", event.character().getName(),
                event.attacker().getName(), event.amount(), event.character().getCurrentHealth());
    }

    @EventListener
    @Order(2)
    void onGamePlayerDied(GamePlayerDied event) {
        CharacterInstance character = event.character();
        ZoneInstance startingZone = character.getWorldInstance().startingZoneInstance()
                .orElseThrow(() -> new IllegalStateException("Aucune starting zone configurée"));

        character.setCurrentHealth(character.getMaxHealth());
        character.moveToZone(startingZone);
        characterDao.update(character);

        character.send(new PlayerRespawned(startingZone.getName()));
        log.info("character.respawned character={} zone={}", character.getName(), startingZone.getName());
    }

    @EventListener
    void onGamePlayerUsedPotion(GamePlayerUsedPotion event) {
        CharacterInstance character = event.character();
        characterDao.update(character);
        character.send(new ItemUsed(event.item().getId(), event.item().getName(), event.item().getRarity(),
                event.healedAmount(), character.getCurrentHealth(), character.getMaxHealth()));
        character.getCurrentZone().broadcast(new CharacterUsedItem(character.getId(), character.getName(),
                event.item().getId(), event.item().getName()), character);
        log.info("character.used_potion character={} item={} healedAmount={}", character.getName(),
                event.item().getName(), event.healedAmount());
    }

    @EventListener
    void onGamePlayerUsedManaPotion(GamePlayerUsedManaPotion event) {
        CharacterInstance character = event.character();
        characterDao.update(character);
        character.send(new ManaPotionUsed(event.item().getId(), event.item().getName(), event.item().getRarity(),
                event.restoredAmount(), character.getCurrentMana(), character.getMaxMana()));
        character.getCurrentZone().broadcast(new CharacterUsedItem(character.getId(), character.getName(),
                event.item().getId(), event.item().getName()), character);
        log.info("character.used_mana_potion character={} item={} restoredAmount={}", character.getName(),
                event.item().getName(), event.restoredAmount());
    }

    @EventListener
    void onCharacterRegenerated(CharacterRegenerated event) {
        CharacterInstance character = event.character();
        characterDao.update(character);
        character.send(new RegenTick(event.hpRestored(), event.manaRestored(), character.getCurrentHealth(),
                character.getMaxHealth(), character.getCurrentMana(), character.getMaxMana()));
        log.info("character.regenerated character={} hpRestored={} manaRestored={}", character.getName(),
                event.hpRestored(), event.manaRestored());
    }

}
