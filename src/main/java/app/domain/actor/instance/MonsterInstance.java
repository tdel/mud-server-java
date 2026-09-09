package app.domain.actor.instance;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import app.domain.ActiveEffect;
import app.domain.ActiveSkill;
import app.domain.PassiveSkill;
import app.domain.actor.Attribute;
import app.domain.actor.AbstractCharacter;
import app.domain.actor.ModifiedStat;
import app.domain.item.LootTableEntry;
import app.domain.map.Position;
import app.game.engine.MonsterAiEngine;

public final class MonsterInstance extends AbstractCharacter {

    private final Position spawnPosition;

    private final int aggroRadius;

    public volatile MonsterAiEngine.PursuitState pursuit;

    public MonsterInstance(UUID id, String name, Map<Attribute, Integer> attributes, int maxHealth,
            Map<ModifiedStat, Integer> baseStats, Position spawnPosition, Set<ActiveSkill> knownSkills,
            Set<PassiveSkill> knownPassiveSkills, List<ActiveEffect> activeEffects, int level, int aggroRadius,
            int xpReward, int goldReward, List<LootTableEntry> lootTable) {
        super(id, name, attributes, maxHealth, maxHealth,
                knownSkills.stream().collect(Collectors.toMap(skill -> skill, skill -> 1)),
                knownPassiveSkills.stream().collect(Collectors.toMap(skill -> skill, skill -> 1)), activeEffects,
                baseStats, false, xpReward, goldReward, lootTable, level, 0, 0, List.of(), null, null);
        this.spawnPosition = spawnPosition;
        this.aggroRadius = aggroRadius;
    }

    public int getAggroRadius() {
        return aggroRadius;
    }

    public Position getSpawnPosition() {
        return spawnPosition;
    }

    @Override
    public String toString() {
        return "GameMonster[id=" + getId() + ", name=" + getName() + ", spawnPosition=" + spawnPosition
                + ", currentHealth=" + getResourceSystem().getCurrentHealth() + ", maxHealth="
                + getResourceSystem().getMaxHealth() + "]";
    }
}
