package fr.idev.mudserver.domain.actor;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class MonsterTemplate {

    private UUID id;
    private String name;
    private String description;
    private int maxHealth;
    private Map<Attribute, Integer> attributes;
    private Integer naturalArmorClass;
    private int xpReward;
    private String naturalDamageDice;
    private int goldReward;
    private List<LootTableEntry> lootTable;
    private int presenceRadius;

    public MonsterTemplate(UUID id, String name, String description, int maxHealth, Map<Attribute, Integer> attributes,
            Integer naturalArmorClass, int xpReward, String naturalDamageDice, int goldReward,
            List<LootTableEntry> lootTable, int presenceRadius) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.maxHealth = maxHealth;
        this.attributes = attributes;
        this.naturalArmorClass = naturalArmorClass;
        this.xpReward = xpReward;
        this.naturalDamageDice = naturalDamageDice;
        this.goldReward = goldReward;
        this.lootTable = lootTable;
        this.presenceRadius = presenceRadius;
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

    public Integer getNaturalArmorClass() {
        return naturalArmorClass;
    }

    public void setNaturalArmorClass(Integer naturalArmorClass) {
        this.naturalArmorClass = naturalArmorClass;
    }

    public int getXpReward() {
        return xpReward;
    }

    public void setXpReward(int xpReward) {
        this.xpReward = xpReward;
    }

    public String getNaturalDamageDice() {
        return naturalDamageDice;
    }

    public void setNaturalDamageDice(String naturalDamageDice) {
        this.naturalDamageDice = naturalDamageDice;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof MonsterTemplate other)) {
            return false;
        }
        return maxHealth == other.maxHealth && xpReward == other.xpReward && goldReward == other.goldReward
                && presenceRadius == other.presenceRadius && Objects.equals(id, other.id)
                && Objects.equals(name, other.name) && Objects.equals(description, other.description)
                && Objects.equals(attributes, other.attributes)
                && Objects.equals(naturalArmorClass, other.naturalArmorClass)
                && Objects.equals(naturalDamageDice, other.naturalDamageDice)
                && Objects.equals(lootTable, other.lootTable);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, description, maxHealth, attributes, naturalArmorClass, xpReward,
                naturalDamageDice, goldReward, lootTable, presenceRadius);
    }

    @Override
    public String toString() {
        return "MonsterTemplate[id=" + id + ", name=" + name + ", description=" + description + ", maxHealth="
                + maxHealth + ", attributes=" + attributes + ", naturalArmorClass=" + naturalArmorClass + ", xpReward="
                + xpReward + ", naturalDamageDice=" + naturalDamageDice + ", goldReward=" + goldReward + ", lootTable="
                + lootTable + ", presenceRadius=" + presenceRadius + "]";
    }

    /**
     * Une entrée de table de butin : {@code dropChance} est une probabilité
     * indépendante entre 0 et 1 (0.001 = 0.1 %, 0.10 = 10 %) — chaque entrée est
     * tirée séparément à la mort du monstre ({@code game.actor.LootService}), donc
     * un même monstre peut faire tomber zéro, un ou plusieurs objets sur un seul
     * kill.
     */
    public record LootTableEntry(UUID itemTemplateId, double dropChance) {
    }
}
