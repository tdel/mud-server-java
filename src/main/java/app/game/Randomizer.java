package app.game;

import java.security.SecureRandom;
import java.util.Random;

public final class Randomizer {

    private static final Random RANDOM = new SecureRandom();

    private Randomizer() {
    }

    public static boolean rollChance(double probability) {
        return RANDOM.nextDouble() < probability;
    }

    public static double randomVariance(double minInclusive, double maxInclusive) {
        return minInclusive + RANDOM.nextDouble() * (maxInclusive - minInclusive);
    }

}
