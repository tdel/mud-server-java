package fr.idev.mudserver.game.dice;

import java.security.SecureRandom;
import java.util.Random;

/**
 * Classe utilitaire statique — sans état d'instance (hors {@link #RANDOM},
 * partagé et thread-safe), jamais un bean Spring : aucun appelant n'a besoin
 * d'injection de dépendances pour de simples jets de dés.
 */
public final class DiceRoller {

    private static final int[] SIMULATED_SIDES = {2, 3};

    private static final Random RANDOM = new SecureRandom();

    private DiceRoller() {
    }

    public static DiceRoll roll(String expression) {
        return roll(DiceExpression.parse(expression));
    }

    /**
     * Tirage de probabilité indépendant (0 à 1), utilisé pour les tables de butin
     * ({@code game.actor.LootService}) plutôt qu'une notation de dés — réutilise le
     * même {@link Random} que {@link #roll}, pas de source d'aléa parallèle.
     */
    public static boolean rollChance(double probability) {
        return RANDOM.nextDouble() < probability;
    }

    public static DiceRoll roll(DiceExpression expression) {
        int[] rolls = new int[expression.count()];
        for (int i = 0; i < expression.count(); i++) {
            rolls[i] = rollDie(expression.sides());
        }
        return new DiceRoll(rolls, expression.modifier());
    }

    /**
     * Jet de d20 unique DnD5e, avec ou sans désavantage (2d20, garde le plus bas —
     * pas de variante avantage pour l'instant, aucun appelant n'en a besoin).
     * Retourne toujours un {@link DiceRoll} à un seul dé dans {@code rolls()} (le
     * d20 finalement retenu) : {@link DiceRoll#total()} ne double donc jamais le
     * résultat même quand deux d20 sont physiquement lancés en interne, et les
     * appelants qui lisent {@code rolls()[0]} comme jet naturel (règle du 1/20
     * naturel côté {@code game.CombatService}) restent valides sans changement.
     */
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
