package fr.idev.mudserver.game.dice;

public record CheckResult(String label, int total, int dc, boolean proficient, boolean disadvantage, boolean success) {
}
