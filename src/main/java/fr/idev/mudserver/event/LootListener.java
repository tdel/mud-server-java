package fr.idev.mudserver.event;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import fr.idev.mudserver.domain.item.Item;
import fr.idev.mudserver.domain.actor.instance.CharacterInstance;
import fr.idev.mudserver.domain.actor.template.MonsterTemplate;
import fr.idev.mudserver.domain.actor.template.MonsterTemplate.LootTableEntry;
import fr.idev.mudserver.domain.actor.event.CharacterDied;
import fr.idev.mudserver.game.dice.DiceRoller;

@Service
public class LootListener {

    private static final Logger log = LoggerFactory.getLogger(LootListener.class);

    @EventListener
    @Order(3)
    @Transactional
    void onCharacterDied(CharacterDied event) {
        MonsterTemplate template = event.character().getTemplate();
        CharacterInstance killer = event.killer();

        if (template.getGoldReward() > 0) {
            killer.receiveGold(template.getGoldReward());
            log.info("loot.gold_dropped killer={} amount={}", killer.getName(), template.getGoldReward());
        }

        for (LootTableEntry entry : template.getLootTable()) {
            if (DiceRoller.rollChance(entry.dropChance())) {
                Item item = new Item(UUID.randomUUID(), entry.itemTemplate(), killer, null);
                killer.receiveLootItem(item);
                log.info("loot.item_dropped killer={} item={}", killer.getName(), item.getName());
            }
        }
    }
}
