package fr.idev.mudserver.game.dice;

public record DiceRoll(int[] rolls, int modifier) {

    public int total() {
        int sum = modifier;
        for (int roll : rolls) {
            sum += roll;
        }
        return sum;
    }
}
