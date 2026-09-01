package app.domain.actor.template;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import app.domain.ActiveEffect;
import app.domain.ActiveSkill;
import app.domain.PassiveSkill;
import app.domain.SkillElement;
import app.domain.actor.Attribute;
import app.domain.item.LootTableEntry;

public class MonsterTemplate {

    private UUID id;
    private String name;
    private int maxHealth;
    private Map<Attribute, Integer> attributes;
    private int pAtk;
    private int mAtk;
    private int pDef;
    private int mDef;
    private int accuracyBonus;
    private int evasionBonus;
    private int critBonus;
    private int xpReward;
    private int goldReward;
    private List<LootTableEntry> lootTable;
    private int aggroRadius;
    private int speed;
    private int atkSpd;
    private int level;
    private Map<SkillElement, Integer> elementalResistances;
    private Set<ActiveSkill> knownSkills;
    private Set<PassiveSkill> knownPassiveSkills;
    private List<ActiveEffect> activeEffects;

    public MonsterTemplate(UUID id, String name, int maxHealth, Map<Attribute, Integer> attributes, int pAtk, int mAtk,
            int pDef, int mDef, int accuracyBonus, int evasionBonus, int critBonus, int xpReward, int goldReward,
            List<LootTableEntry> lootTable, int aggroRadius, int speed, int atkSpd, int level,
            Map<SkillElement, Integer> elementalResistances, Set<ActiveSkill> knownSkills,
            Set<PassiveSkill> knownPassiveSkills, List<ActiveEffect> activeEffects) {
        this.id = id;
        this.name = name;
        this.maxHealth = maxHealth;
        this.attributes = attributes;
        this.pAtk = pAtk;
        this.mAtk = mAtk;
        this.pDef = pDef;
        this.mDef = mDef;
        this.accuracyBonus = accuracyBonus;
        this.evasionBonus = evasionBonus;
        this.critBonus = critBonus;
        this.xpReward = xpReward;
        this.goldReward = goldReward;
        this.lootTable = lootTable;
        this.aggroRadius = aggroRadius;
        this.speed = speed;
        this.atkSpd = atkSpd;
        this.level = level;
        this.elementalResistances = elementalResistances == null ? Map.of() : elementalResistances;
        this.knownSkills = knownSkills == null ? Set.of() : knownSkills;
        this.knownPassiveSkills = knownPassiveSkills == null ? Set.of() : knownPassiveSkills;
        this.activeEffects = activeEffects == null ? List.of() : activeEffects;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public int getMaxHealth() {
        return maxHealth;
    }

    public Map<Attribute, Integer> getAttributes() {
        return attributes;
    }

    public int getPAtk() {
        return pAtk;
    }

    public int getMAtk() {
        return mAtk;
    }

    public int getPDef() {
        return pDef;
    }

    public int getMDef() {
        return mDef;
    }

    public int getAccuracyBonus() {
        return accuracyBonus;
    }

    public int getEvasionBonus() {
        return evasionBonus;
    }

    public int getCritBonus() {
        return critBonus;
    }

    public int getXpReward() {
        return xpReward;
    }

    public int getGoldReward() {
        return goldReward;
    }

    public List<LootTableEntry> getLootTable() {
        return lootTable;
    }

    public int getAggroRadius() {
        return aggroRadius;
    }

    public int getSpeed() {
        return speed;
    }

    public int getAtkSpd() {
        return atkSpd;
    }

    public int getLevel() {
        return level;
    }

    public Map<SkillElement, Integer> getElementalResistances() {
        return elementalResistances;
    }

    public Set<ActiveSkill> getKnownSkills() {
        return knownSkills;
    }

    public Set<PassiveSkill> getKnownPassiveSkills() {
        return knownPassiveSkills;
    }

    public List<ActiveEffect> getActiveEffects() {
        return activeEffects;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof MonsterTemplate other)) {
            return false;
        }
        return maxHealth == other.maxHealth && pAtk == other.pAtk && mAtk == other.mAtk && pDef == other.pDef
                && mDef == other.mDef && accuracyBonus == other.accuracyBonus && evasionBonus == other.evasionBonus
                && critBonus == other.critBonus && xpReward == other.xpReward && goldReward == other.goldReward
                && aggroRadius == other.aggroRadius && speed == other.speed && atkSpd == other.atkSpd
                && level == other.level && Objects.equals(id, other.id) && Objects.equals(name, other.name)
                && Objects.equals(attributes, other.attributes) && Objects.equals(lootTable, other.lootTable)
                && Objects.equals(elementalResistances, other.elementalResistances)
                && Objects.equals(knownSkills, other.knownSkills)
                && Objects.equals(knownPassiveSkills, other.knownPassiveSkills)
                && Objects.equals(activeEffects, other.activeEffects);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, maxHealth, attributes, pAtk, mAtk, pDef, mDef, accuracyBonus, evasionBonus,
                critBonus, xpReward, goldReward, lootTable, aggroRadius, speed, atkSpd, level, elementalResistances,
                knownSkills, knownPassiveSkills, activeEffects);
    }

    @Override
    public String toString() {
        return "MonsterTemplate[id=" + id + ", name=" + name + ", maxHealth=" + maxHealth + ", attributes=" + attributes
                + ", pAtk=" + pAtk + ", mAtk=" + mAtk + ", pDef=" + pDef + ", mDef=" + mDef + ", accuracyBonus="
                + accuracyBonus + ", evasionBonus=" + evasionBonus + ", critBonus=" + critBonus + ", xpReward="
                + xpReward + ", goldReward=" + goldReward + ", lootTable=" + lootTable + ", aggroRadius=" + aggroRadius
                + ", speed=" + speed + ", atkSpd=" + atkSpd + ", level=" + level + ", elementalResistances="
                + elementalResistances + ", knownSkills=" + knownSkills + ", knownPassiveSkills=" + knownPassiveSkills
                + ", activeEffects=" + activeEffects + "]";
    }
}
