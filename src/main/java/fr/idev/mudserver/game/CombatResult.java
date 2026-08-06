package fr.idev.mudserver.game;

public record CombatResult(String targetName, boolean hit, boolean criticalHit, int attackRoll, int armorClass,
        int damage) {
}
