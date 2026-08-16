package fr.idev.mudserver.game.actor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import fr.idev.mudserver.domain.item.Item;
import fr.idev.mudserver.domain.actor.instance.CharacterInstance;
import fr.idev.mudserver.domain.actor.system.InventorySystem;
import fr.idev.mudserver.domain.actor.template.MonsterTemplate;
import fr.idev.mudserver.domain.actor.template.MonsterTemplate.LootResult;
import fr.idev.mudserver.domain.actor.event.CharacterDied;

@Service
public class LootOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(LootOrchestrator.class);

    @EventListener
    @Order(3)
    @Transactional
    void onCharacterDied(CharacterDied event) {
        MonsterTemplate template = event.character().getTemplate();
        CharacterInstance killer = event.killer();

        LootResult loot = template.rollLoot(killer);

        if (loot.gold() > 0) {
            InventorySystem.receiveGold(killer, loot.gold());
            log.info("loot.gold_dropped killer={} amount={}", killer.getName(), loot.gold());
        }

        for (Item item : loot.items()) {
            InventorySystem.receiveLootItem(killer, item);
            log.info("loot.item_dropped killer={} item={}", killer.getName(), item.getName());
        }
    }
}
