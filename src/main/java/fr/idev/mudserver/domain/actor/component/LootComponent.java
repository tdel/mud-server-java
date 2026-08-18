package fr.idev.mudserver.domain.actor.component;

import fr.idev.mudserver.domain.actor.template.MonsterTemplate;

import java.util.List;

public class LootComponent {

    public List<MonsterTemplate.LootTableEntry> lootTable;
    public int xpReward;
    public int goldReward;

    public LootComponent(List<MonsterTemplate.LootTableEntry> lootTable, int xpReward, int goldReward) {
        this.lootTable = lootTable;
        this.xpReward = xpReward;
        this.goldReward = goldReward;
    }
}
