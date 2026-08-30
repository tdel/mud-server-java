package app.game.dice;

import java.security.SecureRandom;
import java.util.Random;

public final class DiceRoller {

    private static final int[] SIMULATED_SIDES = {2, 3};

    private static final Random RANDOM = new SecureRandom();

    private DiceRoller() {
    }

    public static DiceRoll roll(String expression) {
        return roll(DiceExpression.parse(expression));
    }

    public static boolean rollChance(double probability) {
        return RANDOM.nextDouble() < probability;
    }

    public static double randomVariance(double minInclusive, double maxInclusive) {
        return minInclusive + RANDOM.nextDouble() * (maxInclusive - minInclusive);
    }

    public static DiceRoll roll(DiceExpression expression) {
        int[] rolls = new int[expression.count()];
        for (int i = 0; i < expression.count(); i++) {
            rolls[i] = rollDie(expression.sides());
        }
        return new DiceRoll(rolls, expression.modifier());
    }

    public static DiceRoll rollD20(int modifier, boolean disadvantage) {
        int kept = disadvantage ? Math.min(rollDie(20), rollDie(20)) : rollDie(20);
        return new DiceRoll(new int[]{kept}, modifier);
    }

    private static int rollDie(int sides) {
        if (sides == 100) {
            return rollPercentile();
        }

        for (int simulatedSides : SIMULATED_SIDES) {
            if (sides == simulatedSides) {
                // d2/d3 don't exist physically: roll a die with double the sides
                // and halve the result, rounded up.
                return (int) Math.ceil(randomInt(1, sides * 2) / 2.0);
            }
        }

        return randomInt(1, sides);
    }

    private static int rollPercentile() {
        // 2d10: one for the tens digit (0-9), one for the units (0-9) - not
        // a sum. Double 0 is 100, there is no 0 result on a d100.
        int tens = randomInt(0, 9);
        int units = randomInt(0, 9);
        int result = tens * 10 + units;
        return result == 0 ? 100 : result;
    }

    private static int randomInt(int minInclusive, int maxInclusive) {
        return minInclusive + RANDOM.nextInt(maxInclusive - minInclusive + 1);
    }
}
