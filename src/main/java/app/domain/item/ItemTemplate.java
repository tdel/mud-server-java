package app.domain.item;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import app.domain.Spell;

public class ItemTemplate {

    private UUID id;
    private String name;
    private String description;
    private ItemType type;
    private int weight;
    private ArmorCategory armorCategory;
    private int pAtk;
    private int mAtk;
    private int pDef;
    private int mDef;
    private int accuracyBonus;
    private int evasionBonus;
    private int critBonus;
    private int price;
    private Rarity rarity;
    private List<Spell> grantedSpells;

    public ItemTemplate(UUID id, String name, String description, ItemType type, int weight,
            ArmorCategory armorCategory, int pAtk, int mAtk, int pDef, int mDef, int accuracyBonus, int evasionBonus,
            int critBonus, int price, Rarity rarity, List<Spell> grantedSpells) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.type = type;
        this.weight = weight;
        this.armorCategory = armorCategory;
        this.pAtk = pAtk;
        this.mAtk = mAtk;
        this.pDef = pDef;
        this.mDef = mDef;
        this.accuracyBonus = accuracyBonus;
        this.evasionBonus = evasionBonus;
        this.critBonus = critBonus;
        this.price = price;
        this.rarity = rarity;
        this.grantedSpells = grantedSpells == null ? List.of() : grantedSpells;
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

    public int getPAtk() {
        return pAtk;
    }

    public void setPAtk(int pAtk) {
        this.pAtk = pAtk;
    }

    public int getMAtk() {
        return mAtk;
    }

    public void setMAtk(int mAtk) {
        this.mAtk = mAtk;
    }

    public int getPDef() {
        return pDef;
    }

    public void setPDef(int pDef) {
        this.pDef = pDef;
    }

    public int getMDef() {
        return mDef;
    }

    public void setMDef(int mDef) {
        this.mDef = mDef;
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

    public List<Spell> getGrantedSpells() {
        return grantedSpells;
    }

    public void setGrantedSpells(List<Spell> grantedSpells) {
        this.grantedSpells = grantedSpells == null ? List.of() : grantedSpells;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ItemTemplate other)) {
            return false;
        }
        return weight == other.weight && pAtk == other.pAtk && mAtk == other.mAtk && pDef == other.pDef
                && mDef == other.mDef && accuracyBonus == other.accuracyBonus && evasionBonus == other.evasionBonus
                && critBonus == other.critBonus && price == other.price && Objects.equals(id, other.id)
                && Objects.equals(name, other.name) && Objects.equals(description, other.description)
                && type == other.type && armorCategory == other.armorCategory && rarity == other.rarity
                && Objects.equals(grantedSpells, other.grantedSpells);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, description, type, weight, armorCategory, pAtk, mAtk, pDef, mDef, accuracyBonus,
                evasionBonus, critBonus, price, rarity, grantedSpells);
    }

    @Override
    public String toString() {
        return "ItemTemplate[id=" + id + ", name=" + name + ", description=" + description + ", type=" + type
                + ", weight=" + weight + ", armorCategory=" + armorCategory + ", pAtk=" + pAtk + ", mAtk=" + mAtk
                + ", pDef=" + pDef + ", mDef=" + mDef + ", accuracyBonus=" + accuracyBonus + ", evasionBonus="
                + evasionBonus + ", critBonus=" + critBonus + ", price=" + price + ", rarity=" + rarity
                + ", grantedSpells=" + grantedSpells + "]";
    }
}
