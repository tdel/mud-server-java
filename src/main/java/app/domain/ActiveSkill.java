package app.domain;

import java.util.List;
import java.util.UUID;

public record ActiveSkill(UUID id, String name, List<SkillLevel> levels, int reuseTimeMs, int castingTimeMs, int range,
        int aoeRadius, SkillEffectType skillType, SkillTargetType target, SkillElement element, boolean projectile,
        int projectileSpeed, List<SkillEffectDefinition> effects) {

    public int maxLevel() {
        return levels.size();
    }

    public int powerAt(int level) {
        return levels.get(level - 1).power();
    }

    public int manaCostAt(int level) {
        return levels.get(level - 1).mana();
    }
}
