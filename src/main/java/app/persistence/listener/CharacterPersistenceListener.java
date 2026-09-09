package app.persistence.listener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

import app.domain.actor.instance.PlayerInstance;
import app.domain.actor.event.CharacterChoseSubclass;
import app.domain.actor.event.CharacterDamaged;
import app.domain.actor.event.CharacterGainedXp;
import app.domain.actor.event.CharacterKarmaChanged;
import app.domain.actor.event.CharacterReceivedGold;
import app.domain.actor.event.CharacterRecordedPlayerKill;
import app.domain.actor.event.CharacterRecordedPvpKill;
import app.domain.actor.event.CharacterRegenerated;
import app.domain.actor.event.CharacterSpentGold;
import app.domain.actor.event.GamePlayerRespawned;
import app.domain.actor.event.GamePlayerUsedManaPotion;
import app.domain.actor.event.GamePlayerUsedPotion;
import app.domain.actor.event.NewGamePlayerCreated;
import app.domain.actor.event.PlayerPvpFlagCleared;
import app.domain.actor.event.PlayerPvpFlagged;
import app.domain.actor.event.ShotGradeDepleted;
import app.domain.actor.event.ShotGradeToggled;
import app.persistence.CharacterDao;

@Service
public class CharacterPersistenceListener {

    private static final Logger log = LoggerFactory.getLogger(CharacterPersistenceListener.class);

    private final CharacterDao characterDao;

    public CharacterPersistenceListener(CharacterDao characterDao) {
        this.characterDao = characterDao;
    }

    @EventListener
    @Order(1)
    void onNewGamePlayerCreated(NewGamePlayerCreated event) {
        characterDao.insert(event.character());
        log.info("dao.character.NewGamePlayerCreated object={} [accountId={}, race={}, class={}]",
                event.character().getId(), event.character().getAccountId(),
                event.character().getAppearanceSystem().getRace(),
                event.character().getClassSystem().getCharacterClass());
    }

    @EventListener
    void onCharacterGainedXp(CharacterGainedXp event) {
        PlayerInstance character = event.character();
        characterDao.update(character);
        log.info("dao.character.CharacterGainedXp object={} [amount={}, newXp={}, newLevel={}]", character.getId(),
                event.amount(), character.getLevelingSystem().getXp(), character.getLevelingSystem().getLevel());
    }

    @EventListener
    void onCharacterChoseSubclass(CharacterChoseSubclass event) {
        PlayerInstance character = event.character();
        characterDao.update(character);
        log.info("dao.character.CharacterChoseSubclass object={} [tier={}, subclass={}]", character.getId(),
                event.tier(), event.subclass());
    }

    @EventListener
    void onCharacterReceivedGold(CharacterReceivedGold event) {
        characterDao.update(event.character());
        log.info("dao.character.CharacterReceivedGold object={} [amount={}, newGold={}]", event.character().getId(),
                event.amount(), event.character().getInventorySystem().getGold());
    }

    @EventListener
    void onCharacterSpentGold(CharacterSpentGold event) {
        characterDao.update(event.character());
        log.info("dao.character.CharacterSpentGold object={} [amount={}, newGold={}]", event.character().getId(),
                event.amount(), event.character().getInventorySystem().getGold());
    }

    @EventListener
    void onCharacterDamaged(CharacterDamaged event) {
        if (!(event.character() instanceof PlayerInstance character)) {
            return;
        }
        characterDao.update(character);
        log.info("dao.character.CharacterDamaged object={} [attacker={}, amount={}, currentHealth={}]",
                character.getId(), event.attacker().getName(), event.amount(),
                character.getResourceSystem().getCurrentHealth());
    }

    @EventListener
    void onGamePlayerRespawned(GamePlayerRespawned event) {
        PlayerInstance character = event.character();
        characterDao.update(character);
        log.info("dao.character.GamePlayerRespawned object={} [map={}]", character.getId(),
                character.getMotionSystem().getCurrentMap().getName());
    }

    @EventListener
    void onGamePlayerUsedPotion(GamePlayerUsedPotion event) {
        PlayerInstance character = event.character();
        characterDao.update(character);
        log.info("dao.character.GamePlayerUsedPotion object={} [item={}, healedAmount={}]", character.getId(),
                event.item().getName(), event.healedAmount());
    }

    @EventListener
    void onGamePlayerUsedManaPotion(GamePlayerUsedManaPotion event) {
        PlayerInstance character = event.character();
        characterDao.update(character);
        log.info("dao.character.GamePlayerUsedManaPotion object={} [item={}, restoredAmount={}]", character.getId(),
                event.item().getName(), event.restoredAmount());
    }

    @EventListener
    void onCharacterRegenerated(CharacterRegenerated event) {
        PlayerInstance character = event.character();
        characterDao.update(character);
        log.info("dao.character.CharacterRegenerated object={} [hpRestored={}, manaRestored={}]", character.getId(),
                event.hpRestored(), event.manaRestored());
    }

    @EventListener
    void onShotGradeToggled(ShotGradeToggled event) {
        PlayerInstance character = event.character();
        characterDao.update(character);
        log.info("dao.character.ShotGradeToggled object={} [shotType={}, newGrade={}]", character.getId(),
                event.shotType(), event.newGrade());
    }

    @EventListener
    void onShotGradeDepleted(ShotGradeDepleted event) {
        PlayerInstance character = event.character();
        characterDao.update(character);
        log.info("dao.character.ShotGradeDepleted object={} [shotType={}, grade={}]", character.getId(),
                event.shotType(), event.grade());
    }

    @EventListener
    void onCharacterKarmaChanged(CharacterKarmaChanged event) {
        PlayerInstance character = event.character();
        characterDao.update(character);
        log.info("dao.character.CharacterKarmaChanged object={} [newKarma={}]", character.getId(), event.newKarma());
    }

    @EventListener
    void onCharacterRecordedPlayerKill(CharacterRecordedPlayerKill event) {
        PlayerInstance character = event.character();
        characterDao.update(character);
        log.info("dao.character.CharacterRecordedPlayerKill object={} [pkCount={}]", character.getId(),
                character.getPvpSystem().getPkCount());
    }

    @EventListener
    void onCharacterRecordedPvpKill(CharacterRecordedPvpKill event) {
        PlayerInstance character = event.character();
        characterDao.update(character);
        log.info("dao.character.CharacterRecordedPvpKill object={} [pvpCount={}]", character.getId(),
                character.getPvpSystem().getPvpCount());
    }

    // Seul le booléen est persisté, pas l'échéance (voir PlayerInstance) : à la
    // reconnexion, un personnage flaggé reprend pour 10 minutes pleines plutôt
    // que le temps qu'il lui restait à la déconnexion.
    @EventListener
    void onPlayerPvpFlagged(PlayerPvpFlagged event) {
        PlayerInstance character = event.character();
        characterDao.update(character);
        log.info("dao.character.PlayerPvpFlagged object={} [flagged=true]", character.getId());
    }

    @EventListener
    void onPlayerPvpFlagCleared(PlayerPvpFlagCleared event) {
        PlayerInstance character = event.character();
        characterDao.update(character);
        log.info("dao.character.PlayerPvpFlagCleared object={} [flagged=false]", character.getId());
    }

}
