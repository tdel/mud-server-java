package fr.idev.mudserver.domain.item;

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
    private String damageDice;
    private WeaponCategory weaponCategory;
    private int price;
    private Rarity rarity;
    private int bonus;

    public ItemTemplate(UUID id, String name, String description, ItemType type, int weight,
            ArmorCategory armorCategory, int baseAc, String damageDice, WeaponCategory weaponCategory, int price,
            Rarity rarity, int bonus) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.type = type;
        this.weight = weight;
        this.armorCategory = armorCategory;
        this.baseAc = baseAc;
        this.damageDice = damageDice;
        this.weaponCategory = weaponCategory;
        this.price = price;
        this.rarity = rarity;
        this.bonus = bonus;
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

    public String getDamageDice() {
        return damageDice;
    }

    public void setDamageDice(String damageDice) {
        this.damageDice = damageDice;
    }

    public WeaponCategory getWeaponCategory() {
        return weaponCategory;
    }

    public void setWeaponCategory(WeaponCategory weaponCategory) {
        this.weaponCategory = weaponCategory;
    }

    public int getPrice() {
        return price;
    }

    public void setPrice(int price) {
        this.price = price;
    }

    public Rarity getRarity() {
        return rarity;
    }

    public void setRarity(Rarity rarity) {
        this.rarity = rarity;
    }

    public int getBonus() {
        return bonus;
    }

    public void setBonus(int bonus) {
        this.bonus = bonus;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ItemTemplate other)) {
            return false;
        }
        return weight == other.weight && baseAc == other.baseAc && price == other.price && bonus == other.bonus
                && Objects.equals(id, other.id) && Objects.equals(name, other.name)
                && Objects.equals(description, other.description) && type == other.type
                && armorCategory == other.armorCategory && Objects.equals(damageDice, other.damageDice)
                && weaponCategory == other.weaponCategory && rarity == other.rarity;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, description, type, weight, armorCategory, baseAc, damageDice, weaponCategory,
                price, rarity, bonus);
    }

    @Override
    public String toString() {
        return "ItemTemplate[id=" + id + ", name=" + name + ", description=" + description + ", type=" + type
                + ", weight=" + weight + ", armorCategory=" + armorCategory + ", baseAc=" + baseAc + ", damageDice="
                + damageDice + ", weaponCategory=" + weaponCategory + ", price=" + price + ", rarity=" + rarity
                + ", bonus=" + bonus + "]";
    }
}
