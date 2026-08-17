package fr.idev.mudserver.domain.actor.component;

public record LevelingComponent(int level, int xp) {

    public int proficiencyBonus() {
        return 2 + Math.floorDiv(level - 1, 4);
    }
}
