package fr.idev.mudserver.domain.actor.template;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import fr.idev.mudserver.domain.actor.Attribute;
import fr.idev.mudserver.domain.item.ItemTemplate;

public record MonsterTemplate(UUID id, String name, int maxHealth, Map<Attribute, Integer> attributes,
        Integer naturalArmorClass, int xpReward, String naturalDamageDice, int goldReward,
        List<LootTableEntry> lootTable, int aggroRadius, int speed, int level) {

    public record LootTableEntry(ItemTemplate itemTemplate, double dropChance) {
    }

}
