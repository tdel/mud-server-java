package app.domain.actor.template;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import app.domain.Party;
import app.domain.SpellElement;
import app.domain.actor.Attribute;
import app.domain.actor.instance.CharacterInstance;
import app.domain.item.Item;
import app.domain.item.ItemTemplate;
import app.game.dice.DiceRoller;

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
    private int level;
    private Map<SpellElement, Integer> elementalResistances;

    public MonsterTemplate(UUID id, String name, String description, int maxHealth, Map<Attribute, Integer> attributes,
            int naturalPAtk, int naturalMAtk, int naturalPDef, int naturalMDef, int accuracyBonus, int evasionBonus,
            int critBonus, int xpReward, int goldReward, List<LootTableEntry> lootTable, int presenceRadius, int speed,
            int level, Map<SpellElement, Integer> elementalResistances) {
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
        this.level = level;
        this.elementalResistances = elementalResistances == null ? Map.of() : elementalResistances;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public int getMaxHealth() {
        return maxHealth;
    }

    public void setMaxHealth(int maxHealth) {
        this.maxHealth = maxHealth;
    }

    public Map<Attribute, Integer> getAttributes() {
        return attributes;
    }

    public void setAttributes(Map<Attribute, Integer> attributes) {
        this.attributes = attributes;
    }

    public int getNaturalPAtk() {
        return naturalPAtk;
    }

    public void setNaturalPAtk(int naturalPAtk) {
        this.naturalPAtk = naturalPAtk;
    }

    public int getNaturalMAtk() {
        return naturalMAtk;
    }

    public void setNaturalMAtk(int naturalMAtk) {
        this.naturalMAtk = naturalMAtk;
    }

    public int getNaturalPDef() {
        return naturalPDef;
    }

    public void setNaturalPDef(int naturalPDef) {
        this.naturalPDef = naturalPDef;
    }

    public int getNaturalMDef() {
        return naturalMDef;
    }

    public void setNaturalMDef(int naturalMDef) {
        this.naturalMDef = naturalMDef;
    }

    public int getAccuracyBonus() {
        return accuracyBonus;
    }

    public void setAccuracyBonus(int accuracyBonus) {
        this.accuracyBonus = accuracyBonus;
    }

    public int getEvasionBonus() {
        return evasionBonus;
    }

    public void setEvasionBonus(int evasionBonus) {
        this.evasionBonus = evasionBonus;
    }

    public int getCritBonus() {
        return critBonus;
    }

    public void setCritBonus(int critBonus) {
        this.critBonus = critBonus;
    }

    public int getXpReward() {
        return xpReward;
    }

    public void setXpReward(int xpReward) {
        this.xpReward = xpReward;
    }

    public int getGoldReward() {
        return goldReward;
    }

    public void setGoldReward(int goldReward) {
        this.goldReward = goldReward;
    }

    public List<LootTableEntry> getLootTable() {
        return lootTable;
    }

    public void setLootTable(List<LootTableEntry> lootTable) {
        this.lootTable = lootTable;
    }

    public int getPresenceRadius() {
        return presenceRadius;
    }

    public void setPresenceRadius(int presenceRadius) {
        this.presenceRadius = presenceRadius;
    }

    public int getSpeed() {
        return speed;
    }

    public void setSpeed(int speed) {
        this.speed = speed;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public Map<SpellElement, Integer> getElementalResistances() {
        return elementalResistances;
    }

    public void setElementalResistances(Map<SpellElement, Integer> elementalResistances) {
        this.elementalResistances = elementalResistances == null ? Map.of() : elementalResistances;
    }

    public LootResult rollLoot(CharacterInstance killer) {
        List<Item> items = new ArrayList<>();
        for (LootTableEntry entry : lootTable) {
            if (DiceRoller.rollChance(entry.dropChance())) {
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
                member.receiveGold(perMemberGold);
            }
            log.info("loot.gold_dropped killer={} totalGold={} partySize={} perMemberGold={}", killer.getName(),
                    loot.gold(), eligibleMembers.size(), perMemberGold);
        }

        for (Item item : loot.items()) {
            CharacterInstance recipient = party != null ? party.nextLootRecipient(eligibleMembers) : killer;
            recipient.receiveLootItem(item);
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
                && presenceRadius == other.presenceRadius && speed == other.speed && level == other.level
                && Objects.equals(id, other.id) && Objects.equals(name, other.name)
                && Objects.equals(description, other.description) && Objects.equals(attributes, other.attributes)
                && Objects.equals(lootTable, other.lootTable)
                && Objects.equals(elementalResistances, other.elementalResistances);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, description, maxHealth, attributes, naturalPAtk, naturalMAtk, naturalPDef,
                naturalMDef, accuracyBonus, evasionBonus, critBonus, xpReward, goldReward, lootTable, presenceRadius,
                speed, level, elementalResistances);
    }

    @Override
    public String toString() {
        return "MonsterTemplate[id=" + id + ", name=" + name + ", description=" + description + ", maxHealth="
                + maxHealth + ", attributes=" + attributes + ", naturalPAtk=" + naturalPAtk + ", naturalMAtk="
                + naturalMAtk + ", naturalPDef=" + naturalPDef + ", naturalMDef=" + naturalMDef + ", accuracyBonus="
                + accuracyBonus + ", evasionBonus=" + evasionBonus + ", critBonus=" + critBonus + ", xpReward="
                + xpReward + ", goldReward=" + goldReward + ", lootTable=" + lootTable + ", presenceRadius="
                + presenceRadius + ", speed=" + speed + ", level=" + level + ", elementalResistances="
                + elementalResistances + "]";
    }

    public record LootTableEntry(ItemTemplate itemTemplate, double dropChance) {
    }

    public record LootResult(int gold, List<Item> items) {
    }
}
