package fr.idev.mudserver.game;

public record CombatResult(String monsterName, boolean hit, boolean criticalHit, int attackRoll, int armorClass,
        int damage, int remainingHealth, boolean monsterDefeated) {
}
