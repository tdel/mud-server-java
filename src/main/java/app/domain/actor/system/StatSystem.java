package app.domain.actor.system;

import java.util.EnumMap;
import java.util.Map;

import app.domain.actor.Attribute;
import app.domain.actor.ModifiedStat;

public final class StatSystem {

    private final EffectsSystem effectsSystem;
    private final SkillSystem skillSystem;
    private final Map<ModifiedStat, Integer> base = new EnumMap<>(ModifiedStat.class);
    private Map<ModifiedStat, Integer> setBonuses = Map.of();

    public StatSystem(EffectsSystem effectsSystem, SkillSystem skillSystem, Map<ModifiedStat, Integer> initialBase) {
        this.effectsSystem = effectsSystem;
        this.skillSystem = skillSystem;
        this.base.putAll(initialBase);
    }

    public int getBase(ModifiedStat stat) {
        return base.getOrDefault(stat, 0);
    }

    // Base + bonus de set + effets actifs (buffs/debuffs) + compétences passives
    // de stats (Weapon Mastery, Armor Mastery, Anti Magic...).
    public int getEffective(ModifiedStat stat) {
        return adjust(stat, getBase(stat) + setBonuses.getOrDefault(stat, 0));
    }

    // Applique à `value` les modificateurs d'effets et de passifs portant sur
    // `stat` : additifs d'abord, puis multiplicatifs. Sert aussi aux stats sans
    // base (HP_REGEN/MP_REGEN), appliquées au montant de régénération par tick.
    public int adjust(ModifiedStat stat, int value) {
        int flat = value + effectsSystem.additiveModifier(stat) + skillSystem.passiveAdditive(stat);
        double multiplier = effectsSystem.multiplicativeFactor(stat) * skillSystem.passiveFactor(stat);
        return (int) Math.round(flat * multiplier);
    }

    public void updateBase(Map<ModifiedStat, Integer> newBase) {
        base.clear();
        base.putAll(newBase);
    }

    public void setSetBonuses(Map<ModifiedStat, Integer> setBonuses) {
        this.setBonuses = setBonuses;
    }

    // Appelé après toute mutation de l'équipement (equip/unequip, voir
    // InventorySystem) ou de niveau (applyLevelUp) : p.atk/m.atk/accuracy/...
    // dépendent de l'arme/armure équipée et de level.
    public void recomputeStats(Map<Attribute, Integer> attributes, int level, InventorySystem inventorySystem) {
        int baseSpeed = getBase(ModifiedStat.SPEED);
        updateBase(InventorySystem.computeBaseStats(attributes, level, inventorySystem.getItems(), baseSpeed));
        setSetBonuses(inventorySystem.computeSetBonuses());
    }
}
