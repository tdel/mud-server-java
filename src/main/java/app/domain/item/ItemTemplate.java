package app.domain.item;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import app.domain.Spell;
import app.domain.SpellElement;

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
    private int atkSpd;
    private int price;
    private List<Spell> grantedSpells;
    private Map<SpellElement, Integer> elementalResistances;
    private ItemGrade grade;
    private String setId;

    public ItemTemplate(UUID id, String name, String description, ItemType type, int weight,
            ArmorCategory armorCategory, int pAtk, int mAtk, int pDef, int mDef, int accuracyBonus, int evasionBonus,
            int critBonus, int atkSpd, int price, List<Spell> grantedSpells,
            Map<SpellElement, Integer> elementalResistances, ItemGrade grade, String setId) {
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
        this.atkSpd = atkSpd;
        this.price = price;
        this.grantedSpells = grantedSpells == null ? List.of() : grantedSpells;
        this.elementalResistances = elementalResistances == null ? Map.of() : elementalResistances;
        this.grade = grade == null ? ItemGrade.NOGRADE : grade;
        this.setId = setId;
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

    public int getAtkSpd() {
        return atkSpd;
    }

    public void setAtkSpd(int atkSpd) {
        this.atkSpd = atkSpd;
    }

    public int getPrice() {
        return price;
    }

    public void setPrice(int price) {
        this.price = price;
    }

    public List<Spell> getGrantedSpells() {
        return grantedSpells;
    }

    public void setGrantedSpells(List<Spell> grantedSpells) {
        this.grantedSpells = grantedSpells == null ? List.of() : grantedSpells;
    }

    public Map<SpellElement, Integer> getElementalResistances() {
        return elementalResistances;
    }

    public void setElementalResistances(Map<SpellElement, Integer> elementalResistances) {
        this.elementalResistances = elementalResistances == null ? Map.of() : elementalResistances;
    }

    public ItemGrade getGrade() {
        return grade;
    }

    public void setGrade(ItemGrade grade) {
        this.grade = grade == null ? ItemGrade.NOGRADE : grade;
    }

    public String getSetId() {
        return setId;
    }

    public void setSetId(String setId) {
        this.setId = setId;
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
                && critBonus == other.critBonus && atkSpd == other.atkSpd && price == other.price
                && Objects.equals(id, other.id) && Objects.equals(name, other.name)
                && Objects.equals(description, other.description) && type == other.type
                && armorCategory == other.armorCategory && Objects.equals(grantedSpells, other.grantedSpells)
                && Objects.equals(elementalResistances, other.elementalResistances) && grade == other.grade
                && Objects.equals(setId, other.setId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, description, type, weight, armorCategory, pAtk, mAtk, pDef, mDef, accuracyBonus,
                evasionBonus, critBonus, atkSpd, price, grantedSpells, elementalResistances, grade, setId);
    }

    @Override
    public String toString() {
        return "ItemTemplate[id=" + id + ", name=" + name + ", description=" + description + ", type=" + type
                + ", weight=" + weight + ", armorCategory=" + armorCategory + ", pAtk=" + pAtk + ", mAtk=" + mAtk
                + ", pDef=" + pDef + ", mDef=" + mDef + ", accuracyBonus=" + accuracyBonus + ", evasionBonus="
                + evasionBonus + ", critBonus=" + critBonus + ", atkSpd=" + atkSpd + ", price=" + price
                + ", grantedSpells=" + grantedSpells + ", elementalResistances=" + elementalResistances + ", grade="
                + grade + ", setId=" + setId + "]";
    }
}
