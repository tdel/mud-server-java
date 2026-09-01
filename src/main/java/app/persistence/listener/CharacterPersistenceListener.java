package app.persistence.listener;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

import app.domain.Party;
import app.domain.actor.instance.CharacterInstance;
import app.domain.actor.instance.MonsterInstance;
import app.domain.actor.event.CharacterChoseSubclass;
import app.domain.actor.event.CharacterDamaged;
import app.domain.actor.event.CharacterDied;
import app.domain.actor.event.CharacterGainedXp;
import app.domain.actor.event.CharacterLeveledUp;
import app.domain.actor.event.CharacterReceivedGold;
import app.domain.actor.event.CharacterRegenerated;
import app.domain.actor.event.CharacterSpentGold;
import app.domain.actor.event.GamePlayerRespawned;
import app.domain.actor.event.GamePlayerUsedManaPotion;
import app.domain.actor.event.GamePlayerUsedPotion;
import app.domain.actor.event.NewGamePlayerCreated;
import app.domain.actor.event.SubclassChoiceAvailable;
import app.network.message.ingame.CharacterUsedItem;
import app.network.message.ingame.GoldLooted;
import app.network.message.ingame.GoldSpent;
import app.network.message.ingame.ItemUsed;
import app.network.message.ingame.ManaPotionUsed;
import app.network.message.ingame.PartyMemberVitalsUpdated;
import app.network.message.ingame.PlayerLeveledUp;
import app.network.message.ingame.PlayerRespawned;
import app.network.message.ingame.RegenTick;
import app.network.message.ingame.SubclassChoiceOffered;
import app.network.message.ingame.SubclassChosen;
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
        CharacterInstance character = event.character();
        characterDao.update(character);
        log.info("character.xp_gained character={} amount={} newXp={} newLevel={}", character.getName(), event.amount(),
                character.getXp(), character.getLevel());
    }

    @EventListener
    void onCharacterLeveledUp(CharacterLeveledUp event) {
        CharacterInstance character = event.character();
        character.broadcast(new PlayerLeveledUp(character.getName(), event.newLevel()), null);
        broadcastVitalsToParty(character);
        log.info("character.leveled_up character={} newLevel={} hpGained={}", character.getName(), event.newLevel(),
                event.hpGained());
    }

    @EventListener
    void onSubclassChoiceAvailable(SubclassChoiceAvailable event) {
        CharacterInstance character = event.character();
        character.send(new SubclassChoiceOffered(event.tier(), event.options()));
        log.info("character.subclass_choice_available character={} tier={} options={}", character.getName(),
                event.tier(), event.options());
    }

    @EventListener
    void onCharacterChoseSubclass(CharacterChoseSubclass event) {
        CharacterInstance character = event.character();
        characterDao.update(character);
        character.send(new SubclassChosen(event.tier(), event.subclass()));
        log.info("character.subclass_chosen character={} tier={} subclass={}", character.getName(), event.tier(),
                event.subclass());
    }

    @EventListener
    void onCharacterReceivedGold(CharacterReceivedGold event) {
        characterDao.update(event.character());
        event.character().send(new GoldLooted(event.amount()));
        log.info("character.gold_received character={} amount={} newGold={}", event.character().getName(),
                event.amount(), event.character().getInventorySystem().getGold());
    }

    @EventListener
    void onCharacterSpentGold(CharacterSpentGold event) {
        characterDao.update(event.character());
        event.character().send(new GoldSpent(event.amount()));
        log.info("character.gold_spent character={} amount={} newGold={}", event.character().getName(), event.amount(),
                event.character().getInventorySystem().getGold());
    }

    @EventListener
    void onCharacterDied(CharacterDied event) {
        if (!(event.character() instanceof MonsterInstance monster)
                || !(event.killer() instanceof CharacterInstance killer)) {
            return;
        }
        int xpReward = monster.getXpReward();
        Party party = killer.getParty();

        List<CharacterInstance> eligible = party != null
                ? party.getMembers().stream().filter(
                        member -> member.getMotionSystem().getCurrentMap() == killer.getMotionSystem().getCurrentMap())
                        .toList()
                : List.of(killer);

        double multiplier = party != null ? party.shareMultiplier(eligible.size()) : 1.0;
        int perMemberXp = (int) (xpReward * multiplier) / eligible.size();
        for (CharacterInstance member : eligible) {
            member.gainXp(perMemberXp);
        }
        killer.getCombatSystem().setTarget(null);
        log.info("combat.kill_credited killer={} monster={} xpReward={} partySize={} perMemberXp={}", killer.getName(),
                monster.getName(), xpReward, eligible.size(), perMemberXp);
    }

    @EventListener
    void onCharacterDamaged(CharacterDamaged event) {
        if (!(event.character() instanceof CharacterInstance character)) {
            return;
        }
        characterDao.update(character);
        broadcastVitalsToParty(character);
        log.info("combat.damage_taken character={} attacker={} amount={} currentHealth={}", character.getName(),
                event.attacker().getName(), event.amount(), character.getCurrentHealth());
    }

    @EventListener
    void onGamePlayerRespawned(GamePlayerRespawned event) {
        CharacterInstance character = event.character();
        characterDao.update(character);

        character.send(new PlayerRespawned(character.getMotionSystem().getCurrentMap().getName(),
                character.getMotionSystem().getPosition().x(), character.getMotionSystem().getPosition().y(),
                character.getCurrentHealth(), character.getMaxHealth(), character.getCurrentMana(),
                character.getMaxMana()));
        broadcastVitalsToParty(character);
        log.info("character.respawned character={} map={}", character.getName(),
                character.getMotionSystem().getCurrentMap().getName());
    }

    @EventListener
    void onGamePlayerUsedPotion(GamePlayerUsedPotion event) {
        CharacterInstance character = event.character();
        characterDao.update(character);
        character.send(new ItemUsed(event.item().getId(), event.item().getName(), event.item().getGrade(),
                event.healedAmount(), character.getCurrentHealth(), character.getMaxHealth()));
        character.broadcast(new CharacterUsedItem(character.getId(), character.getName(), event.item().getId(),
                event.item().getName()), character);
        broadcastVitalsToParty(character);
        log.info("character.used_potion character={} item={} healedAmount={}", character.getName(),
                event.item().getName(), event.healedAmount());
    }

    @EventListener
    void onGamePlayerUsedManaPotion(GamePlayerUsedManaPotion event) {
        CharacterInstance character = event.character();
        characterDao.update(character);
        character.send(new ManaPotionUsed(event.item().getId(), event.item().getName(), event.item().getGrade(),
                event.restoredAmount(), character.getCurrentMana(), character.getMaxMana()));
        character.broadcast(new CharacterUsedItem(character.getId(), character.getName(), event.item().getId(),
                event.item().getName()), character);
        broadcastVitalsToParty(character);
        log.info("character.used_mana_potion character={} item={} restoredAmount={}", character.getName(),
                event.item().getName(), event.restoredAmount());
    }

    @EventListener
    void onCharacterRegenerated(CharacterRegenerated event) {
        CharacterInstance character = event.character();
        characterDao.update(character);
        character.send(new RegenTick(event.hpRestored(), event.manaRestored(), character.getCurrentHealth(),
                character.getMaxHealth(), character.getCurrentMana(), character.getMaxMana()));
        broadcastVitalsToParty(character);
        log.info("character.regenerated character={} hpRestored={} manaRestored={}", character.getName(),
                event.hpRestored(), event.manaRestored());
    }

    private void broadcastVitalsToParty(CharacterInstance character) {
        Party party = character.getParty();
        if (party != null) {
            party.broadcast(
                    new PartyMemberVitalsUpdated(character.getId(), character.getName(), character.getCurrentHealth(),
                            character.getMaxHealth(), character.getCurrentMana(), character.getMaxMana()),
                    character);
        }
    }

}
