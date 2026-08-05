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

    public MonsterTemplate(UUID id, String name, String description, int maxHealth, Map<Attribute, Integer> attributes,
            Integer naturalArmorClass) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.maxHealth = maxHealth;
        this.attributes = attributes;
        this.naturalArmorClass = naturalArmorClass;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof MonsterTemplate other)) {
            return false;
        }
        return maxHealth == other.maxHealth && Objects.equals(id, other.id) && Objects.equals(name, other.name)
                && Objects.equals(description, other.description) && Objects.equals(attributes, other.attributes)
                && Objects.equals(naturalArmorClass, other.naturalArmorClass);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, description, maxHealth, attributes, naturalArmorClass);
    }

    @Override
    public String toString() {
        return "MonsterTemplate[id=" + id + ", name=" + name + ", description=" + description + ", maxHealth="
                + maxHealth + ", attributes=" + attributes + ", naturalArmorClass=" + naturalArmorClass + "]";
    }
}
