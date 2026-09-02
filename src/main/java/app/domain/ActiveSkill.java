package app.domain;

import java.util.List;
import java.util.UUID;

public record ActiveSkill(UUID id, String name, List<Integer> manaCost, int cooldownSeconds, int castingTimeMs,
        int range, int aoeRadius, SkillEffectType skillType, SkillTargetType target, List<Integer> power,
        SkillElement element, boolean projectile, int projectileSpeed, List<SkillEffectDefinition> effects) {

    public int maxLevel() {
        return power.size();
    }

    public int powerAt(int level) {
        return power.get(level - 1);
    }

    public int manaCostAt(int level) {
        return manaCost.get(level - 1);
    }
}
