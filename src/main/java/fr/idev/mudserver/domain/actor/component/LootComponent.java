package fr.idev.mudserver.domain.actor.component;

import fr.idev.mudserver.domain.actor.template.MonsterTemplate;

import java.util.List;

public record LootComponent(List<MonsterTemplate.LootTableEntry> lootTable, int xpReward, int goldReward) {
}
