package fr.idev.mudserver.domain;

import java.util.Objects;
import java.util.UUID;

public class ItemTemplate {

    private UUID id;
    private String name;
    private String description;
    private ItemType type;
    private int weight;
    private ArmorCategory armorCategory;
    private int baseAc;

    public ItemTemplate(UUID id, String name, String description, ItemType type, int weight,
            ArmorCategory armorCategory, int baseAc) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.type = type;
        this.weight = weight;
        this.armorCategory = armorCategory;
        this.baseAc = baseAc;
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

    public ItemType getType() {
        return type;
    }

    public void setType(ItemType type) {
        this.type = type;
    }

    public int getWeight() {
        return weight;
    }

    public void setWeight(int weight) {
        this.weight = weight;
    }

    public ArmorCategory getArmorCategory() {
        return armorCategory;
    }

    public void setArmorCategory(ArmorCategory armorCategory) {
        this.armorCategory = armorCategory;
    }

    public int getBaseAc() {
        return baseAc;
    }

    public void setBaseAc(int baseAc) {
        this.baseAc = baseAc;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ItemTemplate other)) {
            return false;
        }
        return weight == other.weight && baseAc == other.baseAc && Objects.equals(id, other.id)
                && Objects.equals(name, other.name) && Objects.equals(description, other.description)
                && type == other.type && armorCategory == other.armorCategory;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, description, type, weight, armorCategory, baseAc);
    }

    @Override
    public String toString() {
        return "ItemTemplate[id=" + id + ", name=" + name + ", description=" + description + ", type=" + type
                + ", weight=" + weight + ", armorCategory=" + armorCategory + ", baseAc=" + baseAc + "]";
    }
}
