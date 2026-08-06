package fr.idev.mudserver.domain.actor;

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

    public MonsterTemplate(UUID id, String name, String description, int maxHealth, Map<Attribute, Integer> attributes,
            Integer naturalArmorClass, int xpReward, String naturalDamageDice) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.maxHealth = maxHealth;
        this.attributes = attributes;
        this.naturalArmorClass = naturalArmorClass;
        this.xpReward = xpReward;
        this.naturalDamageDice = naturalDamageDice;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof MonsterTemplate other)) {
            return false;
        }
        return maxHealth == other.maxHealth && xpReward == other.xpReward && Objects.equals(id, other.id)
                && Objects.equals(name, other.name) && Objects.equals(description, other.description)
                && Objects.equals(attributes, other.attributes)
                && Objects.equals(naturalArmorClass, other.naturalArmorClass)
                && Objects.equals(naturalDamageDice, other.naturalDamageDice);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, description, maxHealth, attributes, naturalArmorClass, xpReward,
                naturalDamageDice);
    }

    @Override
    public String toString() {
        return "MonsterTemplate[id=" + id + ", name=" + name + ", description=" + description + ", maxHealth="
                + maxHealth + ", attributes=" + attributes + ", naturalArmorClass=" + naturalArmorClass + ", xpReward="
                + xpReward + ", naturalDamageDice=" + naturalDamageDice + "]";
    }
}
