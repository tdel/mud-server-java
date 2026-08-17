package fr.idev.mudserver.domain.actor.system;

import fr.idev.mudserver.domain.actor.component.IdentityComponent;

import fr.idev.mudserver.domain.actor.component.LootComponent;
import fr.idev.mudserver.domain.actor.instance.MonsterInstance;
import fr.idev.mudserver.domain.item.ItemTemplate;
import fr.idev.mudserver.game.dice.DiceRoller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import fr.idev.mudserver.domain.item.Item;
import fr.idev.mudserver.domain.actor.instance.CharacterInstance;
import fr.idev.mudserver.domain.actor.template.MonsterTemplate;
import fr.idev.mudserver.domain.actor.event.CharacterDied;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class LootSystem {

    private static final Logger log = LoggerFactory.getLogger(LootSystem.class);

    private final InventorySystem inventorySystem;

    public LootSystem(InventorySystem inventorySystem) {
        this.inventorySystem = inventorySystem;
    }

    private LootResult rollLoot(MonsterInstance killed, CharacterInstance killer) {
        List<ItemTemplate> items = new ArrayList<>();
        for (MonsterTemplate.LootTableEntry entry : killed.component(LootComponent.class).lootTable()) {
            if (DiceRoller.rollChance(entry.dropChance())) {
                items.add(entry.itemTemplate());
            }
        }
        return new LootResult(killed.component(LootComponent.class).goldReward(), items);
    }

    @EventListener
    @Order(3)
    @Transactional
    void onCharacterDied(CharacterDied event) {
        MonsterInstance killed = event.character();
        CharacterInstance killer = event.killer();

        LootResult loot = rollLoot(killed, killer);

        if (loot.gold() > 0) {
            inventorySystem.receiveGold(killer, loot.gold());
            log.info("loot.gold_dropped killer={} killed={} amount={}",
                    killer.component(IdentityComponent.class).name(), killed.component(IdentityComponent.class).name(),
                    loot.gold());
        }

        for (ItemTemplate itemTemplate : loot.items()) {
            // TODO: possible piste d'amélioration sur le suivi de l'UUID généré ici
            Item item = new Item(UUID.randomUUID(), itemTemplate, killer, null);
            inventorySystem.receiveLootItem(killer, item);
            log.info("loot.item_dropped killer={} killed={} item={}", killer.component(IdentityComponent.class).name(),
                    killed.component(IdentityComponent.class).name(), item.getName());
        }
    }

    public record LootResult(int gold, List<ItemTemplate> items) {
    }
}
