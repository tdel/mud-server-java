package app.domain;

import java.util.List;
import java.util.UUID;

import app.domain.item.ItemGrade;

public record PassiveSkill(UUID id, String name, List<GradeLevel> levels) {

    public int maxLevel() {
        return levels.size();
    }

    public ItemGrade gradeAt(int level) {
        return levels.get(level - 1).grade();
    }

    public record GradeLevel(int level, ItemGrade grade) {
    }
}
