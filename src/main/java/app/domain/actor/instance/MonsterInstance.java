package app.domain.actor.instance;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import app.domain.ActiveEffect;
import app.domain.ActiveSkill;
import app.domain.Party;
import app.domain.PassiveSkill;
import app.domain.SkillElement;
import app.domain.actor.Attribute;
import app.domain.actor.AbstractCharacter;
import app.domain.actor.ModifiedStat;
import app.domain.item.Item;
import app.domain.item.LootResult;
import app.domain.item.LootTableEntry;
import app.domain.map.Position;
import app.game.engine.MonsterAiEngine;
import app.game.Randomizer;

public final class MonsterInstance extends AbstractCharacter {

    private static final Logger log = LoggerFactory.getLogger(MonsterInstance.class);

    private final Position spawnPosition;

    private final int level;
    private final int aggroRadius;
    private final Map<SkillElement, Integer> elementalResistances;
    private final int xpReward;
    private final int goldReward;
    private final List<LootTableEntry> lootTable;

    public volatile MonsterAiEngine.PursuitState pursuit;

    public MonsterInstance(UUID id, String name, Map<Attribute, Integer> attributes, int maxHealth,
            Map<ModifiedStat, Integer> baseStats, Position spawnPosition, Set<ActiveSkill> knownSkills,
            Set<PassiveSkill> knownPassiveSkills, List<ActiveEffect> activeEffects, int level, int aggroRadius,
            Map<SkillElement, Integer> elementalResistances, int xpReward, int goldReward,
            List<LootTableEntry> lootTable) {
        super(id, name, attributes, maxHealth, maxHealth, knownSkills, knownPassiveSkills, activeEffects, baseStats,
                false);
        this.spawnPosition = spawnPosition;
        this.level = level;
        this.aggroRadius = aggroRadius;
        this.elementalResistances = elementalResistances;
        this.xpReward = xpReward;
        this.goldReward = goldReward;
        this.lootTable = lootTable;
    }

    private LootResult rollLoot(CharacterInstance killer) {
        List<Item> items = new ArrayList<>();
        for (LootTableEntry entry : lootTable) {
            if (Randomizer.rollChance(entry.dropChance())) {
                items.add(new Item(UUID.randomUUID(), entry.itemTemplate(), killer, null));
            }
        }
        return new LootResult(goldReward, items);
    }

    public LootResult grantLootTo(CharacterInstance killer, Party party, List<CharacterInstance> eligibleMembers,
            double goldShareMultiplier) {
        LootResult loot = rollLoot(killer);

        if (loot.gold() > 0) {
            int perMemberGold = (int) (loot.gold() * goldShareMultiplier) / eligibleMembers.size();
            for (CharacterInstance member : eligibleMembers) {
                member.getInventorySystem().receiveGold(perMemberGold);
            }
            log.info("loot.gold_dropped killer={} totalGold={} partySize={} perMemberGold={}", killer.getName(),
                    loot.gold(), eligibleMembers.size(), perMemberGold);
        }

        for (Item item : loot.items()) {
            CharacterInstance recipient = party != null ? party.nextLootRecipient(eligibleMembers) : killer;
            recipient.getInventorySystem().receiveLootItem(item);
            log.info("loot.item_dropped killer={} recipient={} item={}", killer.getName(), recipient.getName(),
                    item.getName());
        }

        return loot;
    }

    public int getXpReward() {
        return xpReward;
    }

    public int getAggroRadius() {
        return aggroRadius;
    }

    public int getLevel() {
        return level;
    }

    @Override
    protected Map<SkillElement, Integer> elementalResistanceMap() {
        return elementalResistances;
    }

    public Position getSpawnPosition() {
        return spawnPosition;
    }

    @Override
    public String toString() {
        return "GameMonster[id=" + getId() + ", name=" + getName() + ", spawnPosition=" + spawnPosition
                + ", currentHealth=" + getCurrentHealth() + ", maxHealth=" + getMaxHealth() + "]";
    }
}
