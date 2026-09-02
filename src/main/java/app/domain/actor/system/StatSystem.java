package app.domain.actor.system;

import java.util.EnumMap;
import java.util.Map;

import app.domain.actor.ModifiedStat;

public final class StatSystem {

    private final EffectsSystem effectsSystem;
    private final Map<ModifiedStat, Integer> base = new EnumMap<>(ModifiedStat.class);
    private Map<ModifiedStat, Integer> setBonuses = Map.of();

    public StatSystem(EffectsSystem effectsSystem, Map<ModifiedStat, Integer> initialBase) {
        this.effectsSystem = effectsSystem;
        this.base.putAll(initialBase);
    }

    public int getBase(ModifiedStat stat) {
        return base.getOrDefault(stat, 0);
    }

    public int getEffective(ModifiedStat stat) {
        int flat = getBase(stat) + setBonuses.getOrDefault(stat, 0) + effectsSystem.additiveModifier(stat);
        double multiplier = effectsSystem.multiplicativeFactor(stat);
        return (int) Math.round(flat * multiplier);
    }

    public void updateBase(Map<ModifiedStat, Integer> newBase) {
        base.clear();
        base.putAll(newBase);
    }

    public void setSetBonuses(Map<ModifiedStat, Integer> setBonuses) {
        this.setBonuses = setBonuses;
    }
}
