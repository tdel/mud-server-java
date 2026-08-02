package fr.idev.mudserver.domain;

import java.util.Objects;
import java.util.UUID;

public class ItemTemplate {

    private UUID id;
    private String name;
    private String description;
    private ItemType type;
    private int weight;

    public ItemTemplate(UUID id, String name, String description, ItemType type, int weight) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.type = type;
        this.weight = weight;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ItemTemplate other)) {
            return false;
        }
        return weight == other.weight && Objects.equals(id, other.id) && Objects.equals(name, other.name)
                && Objects.equals(description, other.description) && type == other.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, description, type, weight);
    }

    @Override
    public String toString() {
        return "ItemTemplate[id=" + id + ", name=" + name + ", description=" + description + ", type=" + type
                + ", weight=" + weight + "]";
    }
}
