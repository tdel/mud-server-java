package fr.idev.mudserver.game.actor;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import fr.idev.mudserver.domain.Item;
import fr.idev.mudserver.domain.actor.GamePlayer;
import fr.idev.mudserver.domain.actor.MonsterTemplate;
import fr.idev.mudserver.domain.actor.MonsterTemplate.LootTableEntry;
import fr.idev.mudserver.domain.actor.event.CharacterDied;
import fr.idev.mudserver.game.dice.DiceRoller;

/**
 * Décide du butin d'un monstre mort — l'or (100 % de succès, montant fixe) et
 * chaque entrée de sa table d'équipement (tirage indépendant, {@link DiceRoller
 * #rollChance}) — mais ne persiste rien lui-même : {@link GamePlayer
 * #receiveGold}/{@link GamePlayer#receiveLootItem} mutent l'état en mémoire et
 * publient chacun leur propre événement, dont {@code CharacterService}/
 * {@code ItemService} font la persistance et l'envoi du message au joueur (même
 * séparation que {@code CharacterService#onCharacterDied} pour l'XP). Le butin
 * ne va jamais qu'au tueur, jamais à la room — pas de {@code Room.broadcast}
 * ici.
 *
 * <p>
 * {@code @Order(3)} : s'exécute après {@code RoomService#onCharacterDied}
 * (@Order(1), annonce {@code MonsterDefeated} à la room) et {@code
 * CharacterService#onCharacterDied} (@Order(2), crédit d'XP), avant {@code
 * CombatEngine#onCharacterDied} (pas d'@Order, nettoyage de l'encounter) — le
 * joueur voit donc la mort du monstre, puis l'XP/niveau, puis son butin, dans
 * cet ordre.
 *
 * <p>
 * {@code onCharacterDied} est {@code @Transactional} bien que cette classe ne
 * détienne aucun DAO : {@code receiveGold}/{@code receiveLootItem} publient
 * chacun un événement traité en synchrone, sur ce même thread, par {@code
 * CharacterService#onCharacterReceivedGold} ({@code characterDao.update}) et
 * {@code ItemService#onCharacterLootedItem} ({@code itemDao.insert}). Ces deux
 * listeners ne sont pas eux-mêmes {@code @Transactional} ; ils rejoignent la
 * transaction ouverte ici parce que jOOQ résout sa connexion via le
 * gestionnaire de transaction Spring lié au thread courant (même mécanisme que
 * {@code ItemService#onGamePlayerEquippedItem}, sauf qu'ici la frontière
 * transactionnelle traverse plusieurs beans via des événements imbriqués plutôt
 * que d'agir au sein d'une seule méthode). Sans ça, un crash en cours de boucle
 * de butin pouvait créditer l'or sans persister l'item, ou l'inverse.
 */
@Service
public class LootService {

    private static final Logger log = LoggerFactory.getLogger(LootService.class);

    private final DiceRoller diceRoller;

    public LootService(DiceRoller diceRoller) {
        this.diceRoller = diceRoller;
    }

    @EventListener
    @Order(3)
    @Transactional
    void onCharacterDied(CharacterDied event) {
        MonsterTemplate template = event.character().getTemplate();
        GamePlayer killer = event.killer();

        if (template.getGoldReward() > 0) {
            killer.receiveGold(template.getGoldReward());
            log.info("loot.gold_dropped killer={} amount={}", killer.getName(), template.getGoldReward());
        }

        for (LootTableEntry entry : template.getLootTable()) {
            if (diceRoller.rollChance(entry.dropChance())) {
                Item item = new Item(UUID.randomUUID(), entry.itemTemplateId(), null, killer.getId(), null);
                killer.receiveLootItem(item);
                log.info("loot.item_dropped killer={} item={}", killer.getName(), item.getName());
            }
        }
    }
}
