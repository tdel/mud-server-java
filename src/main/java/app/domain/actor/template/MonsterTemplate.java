package app.domain.actor.template;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import app.domain.ActiveEffect;
import app.domain.ActiveSkill;
import app.domain.PassiveSkill;
import app.domain.Party;
import app.domain.SkillElement;
import app.domain.actor.Attribute;
import app.domain.actor.instance.CharacterInstance;
import app.domain.item.Item;
import app.domain.item.ItemTemplate;
import app.game.Randomizer;

public class MonsterTemplate {

    private static final Logger log = LoggerFactory.getLogger(MonsterTemplate.class);

    private UUID id;
    private String name;
    private String description;
    private int maxHealth;
    private Map<Attribute, Integer> attributes;
    private int naturalPAtk;
    private int naturalMAtk;
    private int naturalPDef;
    private int naturalMDef;
    private int accuracyBonus;
    private int evasionBonus;
    private int critBonus;
    private int xpReward;
    private int goldReward;
    private List<LootTableEntry> lootTable;
    private int presenceRadius;
    private int speed;
    private int atkSpd;
    private int level;
    private Map<SkillElement, Integer> elementalResistances;
    private Set<ActiveSkill> knownSkills;
    private Set<PassiveSkill> knownPassiveSkills;
    private List<ActiveEffect> activeEffects;

    public MonsterTemplate(UUID id, String name, String description, int maxHealth, Map<Attribute, Integer> attributes,
            int naturalPAtk, int naturalMAtk, int naturalPDef, int naturalMDef, int accuracyBonus, int evasionBonus,
            int critBonus, int xpReward, int goldReward, List<LootTableEntry> lootTable, int presenceRadius, int speed,
            int atkSpd, int level, Map<SkillElement, Integer> elementalResistances, Set<ActiveSkill> knownSkills,
            Set<PassiveSkill> knownPassiveSkills, List<ActiveEffect> activeEffects) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.maxHealth = maxHealth;
        this.attributes = attributes;
        this.naturalPAtk = naturalPAtk;
        this.naturalMAtk = naturalMAtk;
        this.naturalPDef = naturalPDef;
        this.naturalMDef = naturalMDef;
        this.accuracyBonus = accuracyBonus;
        this.evasionBonus = evasionBonus;
        this.critBonus = critBonus;
        this.xpReward = xpReward;
        this.goldReward = goldReward;
        this.lootTable = lootTable;
        this.presenceRadius = presenceRadius;
        this.speed = speed;
        this.atkSpd = atkSpd;
        this.level = level;
        this.elementalResistances = elementalResistances == null ? Map.of() : elementalResistances;
        this.knownSkills = knownSkills == null ? Set.of() : knownSkills;
        this.knownPassiveSkills = knownPassiveSkills == null ? Set.of() : knownPassiveSkills;
        this.activeEffects = activeEffects == null ? List.of() : activeEffects;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public int getMaxHealth() {
        return maxHealth;
    }

    public Map<Attribute, Integer> getAttributes() {
        return attributes;
    }

    public int getNaturalPAtk() {
        return naturalPAtk;
    }

    public int getNaturalMAtk() {
        return naturalMAtk;
    }

    public int getNaturalPDef() {
        return naturalPDef;
    }

    public int getNaturalMDef() {
        return naturalMDef;
    }

    public int getAccuracyBonus() {
        return accuracyBonus;
    }

    public int getEvasionBonus() {
        return evasionBonus;
    }

    public int getCritBonus() {
        return critBonus;
    }

    public int getXpReward() {
        return xpReward;
    }

    public int getGoldReward() {
        return goldReward;
    }

    public List<LootTableEntry> getLootTable() {
        return lootTable;
    }

    public int getPresenceRadius() {
        return presenceRadius;
    }

    public int getSpeed() {
        return speed;
    }

    public int getAtkSpd() {
        return atkSpd;
    }

    public int getLevel() {
        return level;
    }

    public Map<SkillElement, Integer> getElementalResistances() {
        return elementalResistances;
    }

    public Set<ActiveSkill> getKnownSkills() {
        return knownSkills;
    }

    public Set<PassiveSkill> getKnownPassiveSkills() {
        return knownPassiveSkills;
    }

    public List<ActiveEffect> getActiveEffects() {
        return activeEffects;
    }

    public LootResult rollLoot(CharacterInstance killer) {
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

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof MonsterTemplate other)) {
            return false;
        }
        return maxHealth == other.maxHealth && naturalPAtk == other.naturalPAtk && naturalMAtk == other.naturalMAtk
                && naturalPDef == other.naturalPDef && naturalMDef == other.naturalMDef
                && accuracyBonus == other.accuracyBonus && evasionBonus == other.evasionBonus
                && critBonus == other.critBonus && xpReward == other.xpReward && goldReward == other.goldReward
                && presenceRadius == other.presenceRadius && speed == other.speed && atkSpd == other.atkSpd
                && level == other.level && Objects.equals(id, other.id) && Objects.equals(name, other.name)
                && Objects.equals(description, other.description) && Objects.equals(attributes, other.attributes)
                && Objects.equals(lootTable, other.lootTable)
                && Objects.equals(elementalResistances, other.elementalResistances)
                && Objects.equals(knownSkills, other.knownSkills)
                && Objects.equals(knownPassiveSkills, other.knownPassiveSkills)
                && Objects.equals(activeEffects, other.activeEffects);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, description, maxHealth, attributes, naturalPAtk, naturalMAtk, naturalPDef,
                naturalMDef, accuracyBonus, evasionBonus, critBonus, xpReward, goldReward, lootTable, presenceRadius,
                speed, atkSpd, level, elementalResistances, knownSkills, knownPassiveSkills, activeEffects);
    }

    @Override
    public String toString() {
        return "MonsterTemplate[id=" + id + ", name=" + name + ", description=" + description + ", maxHealth="
                + maxHealth + ", attributes=" + attributes + ", naturalPAtk=" + naturalPAtk + ", naturalMAtk="
                + naturalMAtk + ", naturalPDef=" + naturalPDef + ", naturalMDef=" + naturalMDef + ", accuracyBonus="
                + accuracyBonus + ", evasionBonus=" + evasionBonus + ", critBonus=" + critBonus + ", xpReward="
                + xpReward + ", goldReward=" + goldReward + ", lootTable=" + lootTable + ", presenceRadius="
                + presenceRadius + ", speed=" + speed + ", atkSpd=" + atkSpd + ", level=" + level
                + ", elementalResistances=" + elementalResistances + ", knownSkills=" + knownSkills
                + ", knownPassiveSkills=" + knownPassiveSkills + ", activeEffects=" + activeEffects + "]";
    }

    public record LootTableEntry(ItemTemplate itemTemplate, double dropChance) {
    }

    public record LootResult(int gold, List<Item> items) {
    }
}
