package fr.idev.mudserver.game.actor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

import fr.idev.mudserver.domain.actor.Attribute;
import fr.idev.mudserver.domain.actor.GamePlayer;
import fr.idev.mudserver.domain.actor.event.CharacterDied;
import fr.idev.mudserver.domain.actor.event.CharacterGainedXp;
import fr.idev.mudserver.domain.actor.event.CharacterReceivedGold;
import fr.idev.mudserver.domain.actor.event.CharacterSpentGold;
import fr.idev.mudserver.domain.actor.event.GamePlayerDied;
import fr.idev.mudserver.domain.actor.event.GamePlayerUsedPotion;
import fr.idev.mudserver.domain.Room;
import fr.idev.mudserver.game.RoomService;
import fr.idev.mudserver.network.message.ingame.GoldLooted;
import fr.idev.mudserver.network.message.ingame.GoldSpent;
import fr.idev.mudserver.network.message.ingame.ItemUsed;
import fr.idev.mudserver.network.message.ingame.PlayerLeveledUp;
import fr.idev.mudserver.network.message.ingame.PlayerRespawned;
import fr.idev.mudserver.network.message.ingame.XpGained;
import fr.idev.mudserver.persistence.CharacterDao;

/**
 * Réagit à {@link CharacterGainedXp} : persiste l'XP, puis fait franchir à
 * {@code character} autant de paliers de niveau que son XP le permet (une seule
 * mise à mort peut en franchir plusieurs). Le gain de PV par niveau suit la
 * règle 5e « fixe » (alternative au jet de dé) : (dé de vie / 2 + 1) +
 * modificateur de CON, ajouté aux PV max et aux PV courants — pas de soin
 * complet. Cette logique ne peut pas vivre sur {@link GamePlayer} lui-même
 * (simple POJO) puisqu'elle dépend de {@link LevelService}, un bean Spring.
 *
 * <p>
 * Réagit aussi à {@link CharacterDied} : c'est ici, plutôt que dans
 * {@code GameMonster#takeDamage}, que le tueur est crédité de l'XP du monstre
 * et que sa cible est vidée — deux effets qui concernent l'état du personnage
 * joueur, pas la room (voir {@code RoomService#onCharacterDied} pour le pendant
 * « room »).
 */
@Service
public class CharacterService {

    private static final Logger log = LoggerFactory.getLogger(CharacterService.class);

    private final CharacterDao characterDao;
    private final LevelService levelService;
    private final RoomService roomService;

    public CharacterService(CharacterDao characterDao, LevelService levelService, RoomService roomService) {
        this.characterDao = characterDao;
        this.levelService = levelService;
        this.roomService = roomService;
    }

    @EventListener
    void onCharacterGainedXp(CharacterGainedXp event) {
        GamePlayer character = event.character();
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

    /**
     * Persiste l'or gagné (butin, voir {@code game.actor.LootService}) et notifie
     * le joueur seul — jamais de broadcast à la room, contrairement à {@code
     * MonsterDefeated}/{@code PlayerLeveledUp}.
     */
    @EventListener
    void onCharacterReceivedGold(CharacterReceivedGold event) {
        characterDao.update(event.character());
        event.character().send(new GoldLooted(event.amount()));
        log.info("character.gold_received character={} amount={} newGold={}", event.character().getName(),
                event.amount(), event.character().getInventory().getGold());
    }

    /**
     * Symétrique de {@link #onCharacterReceivedGold} pour une dépense (boutique
     * PNJ, voir {@link GamePlayer#buyItem}) : persiste le nouveau solde et confirme
     * au joueur.
     */
    @EventListener
    void onCharacterSpentGold(CharacterSpentGold event) {
        characterDao.update(event.character());
        event.character().send(new GoldSpent(event.amount()));
        log.info("character.gold_spent character={} amount={} newGold={}", event.character().getName(), event.amount(),
                event.character().getInventory().getGold());
    }

    /**
     * {@code @Order(2)} : ce listener s'exécute après
     * {@code RoomService#onCharacterDied} pour ce même événement, afin que le
     * joueur voie la mort du monstre avant le message d'XP (et une éventuelle
     * montée de niveau) que déclenche {@link GamePlayer#gainXp}.
     */
    @EventListener
    @Order(2)
    void onCharacterDied(CharacterDied event) {
        GamePlayer killer = event.killer();
        int xpReward = event.character().getTemplate().getXpReward();
        killer.gainXp(xpReward);
        killer.setTarget(null);
        log.info("combat.kill_credited killer={} monster={} xpReward={}", killer.getName(), event.character().getName(),
                xpReward);
    }

    /**
     * {@code @Order(2)} : s'exécute après {@code RoomService#onGamePlayerDied}, qui
     * a déjà diffusé l'annonce de la mort à la room d'origine avant que
     * {@code moveToRoom} ne l'écrase. Restauration complète des PV, pas de pénalité
     * (XP, niveau...) — non demandée. Le retrait de l'affrontement lui-même est
     * géré par {@code CombatEngine#onGamePlayerDied}, indépendamment de cette
     * méthode.
     */
    @EventListener
    @Order(2)
    void onGamePlayerDied(GamePlayerDied event) {
        GamePlayer character = event.character();
        Room startingRoom = roomService.startingRoom()
                .orElseThrow(() -> new IllegalStateException("Aucune starting room configurée"));

        character.setCurrentHealth(character.getMaxHealth());
        character.moveToRoom(startingRoom);
        characterDao.update(character);

        character.send(new PlayerRespawned(startingRoom.getName()));
        log.info("character.respawned character={} room={}", character.getName(), startingRoom.getName());
    }

    /**
     * {@code ConsumableItem#consume} a déjà mis à jour les PV en mémoire et retiré
     * la potion de l'inventaire — persiste et confirme au joueur, même mécanisme
     * que {@link #onCharacterReceivedGold}.
     */
    @EventListener
    void onGamePlayerUsedPotion(GamePlayerUsedPotion event) {
        GamePlayer character = event.character();
        characterDao.update(character);
        character.send(new ItemUsed(event.item().getName(), event.item().getRarity(), event.healedAmount(),
                character.getCurrentHealth(), character.getMaxHealth()));
        log.info("character.used_potion character={} item={} healedAmount={}", character.getName(),
                event.item().getName(), event.healedAmount());
    }
}
