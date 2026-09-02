package app.domain.item;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import app.domain.ActiveSkill;
import app.domain.SkillElement;

public class EquipmentItem extends ItemTemplate {

    private ArmorCategory armorCategory;
    private int pAtk;
    private int mAtk;
    private int pDef;
    private int mDef;
    private int accuracyBonus;
    private int evasionBonus;
    private int critBonus;
    private int atkSpd;
    private List<ActiveSkill> grantedSkills;
    private Map<SkillElement, Integer> elementalResistances;
    private String setId;
    private ItemExpectation expectation;

    public EquipmentItem(UUID id, String name, String description, ItemType type, int weight,
            ArmorCategory armorCategory, int pAtk, int mAtk, int pDef, int mDef, int accuracyBonus, int evasionBonus,
            int critBonus, int atkSpd, int price, List<ActiveSkill> grantedSkills,
            Map<SkillElement, Integer> elementalResistances, ItemGrade grade, String setId,
            ItemExpectation expectation) {
        super(id, name, description, type, weight, price, grade);
        this.armorCategory = armorCategory;
        this.pAtk = pAtk;
        this.mAtk = mAtk;
        this.pDef = pDef;
        this.mDef = mDef;
        this.accuracyBonus = accuracyBonus;
        this.evasionBonus = evasionBonus;
        this.critBonus = critBonus;
        this.atkSpd = atkSpd;
        this.grantedSkills = grantedSkills == null ? List.of() : grantedSkills;
        this.elementalResistances = elementalResistances == null ? Map.of() : elementalResistances;
        this.setId = setId;
        this.expectation = expectation;
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

    public List<ActiveSkill> getGrantedSkills() {
        return grantedSkills;
    }

    public void setGrantedSkills(List<ActiveSkill> grantedSkills) {
        this.grantedSkills = grantedSkills == null ? List.of() : grantedSkills;
    }

    public Map<SkillElement, Integer> getElementalResistances() {
        return elementalResistances;
    }

    public void setElementalResistances(Map<SkillElement, Integer> elementalResistances) {
        this.elementalResistances = elementalResistances == null ? Map.of() : elementalResistances;
    }

    public String getSetId() {
        return setId;
    }

    public void setSetId(String setId) {
        this.setId = setId;
    }

    public ItemExpectation getExpectation() {
        return expectation;
    }

    public void setExpectation(ItemExpectation expectation) {
        this.expectation = expectation;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof EquipmentItem other)) {
            return false;
        }
        return super.equals(other) && pAtk == other.pAtk && mAtk == other.mAtk && pDef == other.pDef
                && mDef == other.mDef && accuracyBonus == other.accuracyBonus && evasionBonus == other.evasionBonus
                && critBonus == other.critBonus && atkSpd == other.atkSpd && armorCategory == other.armorCategory
                && Objects.equals(grantedSkills, other.grantedSkills)
                && Objects.equals(elementalResistances, other.elementalResistances)
                && Objects.equals(setId, other.setId) && Objects.equals(expectation, other.expectation);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), armorCategory, pAtk, mAtk, pDef, mDef, accuracyBonus, evasionBonus,
                critBonus, atkSpd, grantedSkills, elementalResistances, setId, expectation);
    }

    @Override
    public String toString() {
        return "EquipmentItem[" + super.toString() + ", armorCategory=" + armorCategory + ", pAtk=" + pAtk + ", mAtk="
                + mAtk + ", pDef=" + pDef + ", mDef=" + mDef + ", accuracyBonus=" + accuracyBonus + ", evasionBonus="
                + evasionBonus + ", critBonus=" + critBonus + ", atkSpd=" + atkSpd + ", grantedSkills=" + grantedSkills
                + ", elementalResistances=" + elementalResistances + ", setId=" + setId + ", expectation=" + expectation
                + "]";
    }
}
