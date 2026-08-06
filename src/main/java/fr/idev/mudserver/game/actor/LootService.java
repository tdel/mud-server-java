package fr.idev.mudserver.game.actor;

import java.util.UUID;

import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

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
 */
@Service
public class LootService {

    private final DiceRoller diceRoller;

    public LootService(DiceRoller diceRoller) {
        this.diceRoller = diceRoller;
    }

    @EventListener
    @Order(3)
    void onCharacterDied(CharacterDied event) {
        MonsterTemplate template = event.character().getTemplate();
        GamePlayer killer = event.killer();

        if (template.getGoldReward() > 0) {
            killer.receiveGold(template.getGoldReward());
        }

        for (LootTableEntry entry : template.getLootTable()) {
            if (diceRoller.rollChance(entry.dropChance())) {
                Item item = new Item(UUID.randomUUID(), entry.itemTemplateId(), null, killer.getId(), null);
                killer.receiveLootItem(item);
            }
        }
    }
}
