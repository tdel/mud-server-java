package fr.idev.mudserver.domain.actor.component;

public class LevelingComponent {

    public int level;
    public int xp;

    public LevelingComponent(int level, int xp) {
        this.level = level;
        this.xp = xp;
    }

    public int proficiencyBonus() {
        return 2 + Math.floorDiv(level - 1, 4);
    }
}
