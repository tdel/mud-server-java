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
        log.info("character.created character={} accountId={} race={} class={}", event.character().getName(),
                event.character().getAccountId(), event.character().getAppearanceSystem().getRace(),
                event.character().getClassSystem().getCharacterClass());
    }

    @EventListener
    void onCharacterGainedXp(CharacterGainedXp event) {
        PlayerInstance character = event.character();
        characterDao.update(character);
        log.info("character.xp_gained character={} amount={} newXp={} newLevel={}", character.getName(), event.amount(),
                character.getLevelingSystem().getXp(), character.getLevel());
    }

    @EventListener
    void onCharacterChoseSubclass(CharacterChoseSubclass event) {
        PlayerInstance character = event.character();
        characterDao.update(character);
        log.info("character.subclass_chosen character={} tier={} subclass={}", character.getName(), event.tier(),
                event.subclass());
    }

    @EventListener
    void onCharacterReceivedGold(CharacterReceivedGold event) {
        characterDao.update(event.character());
        log.info("character.gold_received character={} amount={} newGold={}", event.character().getName(),
                event.amount(), event.character().getInventorySystem().getGold());
    }

    @EventListener
    void onCharacterSpentGold(CharacterSpentGold event) {
        characterDao.update(event.character());
        log.info("character.gold_spent character={} amount={} newGold={}", event.character().getName(), event.amount(),
                event.character().getInventorySystem().getGold());
    }

    @EventListener
    void onCharacterDamaged(CharacterDamaged event) {
        if (!(event.character() instanceof PlayerInstance character)) {
            return;
        }
        characterDao.update(character);
        log.info("combat.damage_taken character={} attacker={} amount={} currentHealth={}", character.getName(),
                event.attacker().getName(), event.amount(), character.getResourceSystem().getCurrentHealth());
    }

    @EventListener
    void onGamePlayerRespawned(GamePlayerRespawned event) {
        PlayerInstance character = event.character();
        characterDao.update(character);
        log.info("character.respawned character={} map={}", character.getName(),
                character.getMotionSystem().getCurrentMap().getName());
    }

    @EventListener
    void onGamePlayerUsedPotion(GamePlayerUsedPotion event) {
        PlayerInstance character = event.character();
        characterDao.update(character);
        log.info("character.used_potion character={} item={} healedAmount={}", character.getName(),
                event.item().getName(), event.healedAmount());
    }

    @EventListener
    void onGamePlayerUsedManaPotion(GamePlayerUsedManaPotion event) {
        PlayerInstance character = event.character();
        characterDao.update(character);
        log.info("character.used_mana_potion character={} item={} restoredAmount={}", character.getName(),
                event.item().getName(), event.restoredAmount());
    }

    @EventListener
    void onCharacterRegenerated(CharacterRegenerated event) {
        PlayerInstance character = event.character();
        characterDao.update(character);
        log.info("character.regenerated character={} hpRestored={} manaRestored={}", character.getName(),
                event.hpRestored(), event.manaRestored());
    }

    @EventListener
    void onShotGradeToggled(ShotGradeToggled event) {
        PlayerInstance character = event.character();
        characterDao.update(character);
        log.info("character.shot_grade_toggled character={} shotType={} newGrade={}", character.getName(),
                event.shotType(), event.newGrade());
    }

    @EventListener
    void onShotGradeDepleted(ShotGradeDepleted event) {
        PlayerInstance character = event.character();
        characterDao.update(character);
        log.info("character.shot_grade_depleted character={} shotType={} grade={}", character.getName(),
                event.shotType(), event.grade());
    }

    @EventListener
    void onCharacterKarmaChanged(CharacterKarmaChanged event) {
        PlayerInstance character = event.character();
        characterDao.update(character);
        log.info("character.karma_changed character={} newKarma={}", character.getName(), event.newKarma());
    }

    @EventListener
    void onCharacterRecordedPlayerKill(CharacterRecordedPlayerKill event) {
        PlayerInstance character = event.character();
        characterDao.update(character);
        log.info("character.pk_recorded character={} pkCount={}", character.getName(),
                character.getPvpSystem().getPkCount());
    }

    @EventListener
    void onCharacterRecordedPvpKill(CharacterRecordedPvpKill event) {
        PlayerInstance character = event.character();
        characterDao.update(character);
        log.info("character.pvp_kill_recorded character={} pvpCount={}", character.getName(),
                character.getPvpSystem().getPvpCount());
    }

    // Seul le booléen est persisté, pas l'échéance (voir PlayerInstance) : à la
    // reconnexion, un personnage flaggé reprend pour 10 minutes pleines plutôt
    // que le temps qu'il lui restait à la déconnexion.
    @EventListener
    void onPlayerPvpFlagged(PlayerPvpFlagged event) {
        PlayerInstance character = event.character();
        characterDao.update(character);
        log.info("character.pvp_flagged character={}", character.getName());
    }

    @EventListener
    void onPlayerPvpFlagCleared(PlayerPvpFlagCleared event) {
        PlayerInstance character = event.character();
        characterDao.update(character);
        log.info("character.pvp_flag_cleared character={}", character.getName());
    }

}
