package fr.idev.mudserver.game.dice;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DiceRollerTest {

    @Test
    void rollD20WithoutDisadvantageStaysWithinTheD20Range() {
        int modifier = 3;
        for (int i = 0; i < 1000; i++) {
            DiceRoll roll = DiceRoller.rollD20(modifier, false);

            assertThat(roll.rolls()).hasSize(1);
            assertThat(roll.total()).isBetween(1 + modifier, 20 + modifier);
        }
    }

    @Test
    void rollD20WithDisadvantageStaysWithinTheD20RangeAndNeverDoubleCounts() {
        int modifier = 3;
        for (int i = 0; i < 1000; i++) {
            DiceRoll roll = DiceRoller.rollD20(modifier, true);

            assertThat(roll.rolls()).hasSize(1);
            assertThat(roll.total()).isBetween(1 + modifier, 20 + modifier);
        }
    }

    @Test
    void rollD20WithDisadvantageIsStatisticallyLowerThanWithoutIt() {
        // Moyenne théorique d'1d20 = 10.5, moyenne théorique de 2d20-garde-le-plus-bas
        // ≈ 6.85 — sur 20 000 tirages l'écart est largement au-dessus du bruit
        // statistique, probabilité de faux négatif négligeable.
        int iterations = 20_000;
        long withoutDisadvantageTotal = 0;
        long withDisadvantageTotal = 0;
        for (int i = 0; i < iterations; i++) {
            withoutDisadvantageTotal += DiceRoller.rollD20(0, false).total();
            withDisadvantageTotal += DiceRoller.rollD20(0, true).total();
        }

        double withoutDisadvantageAverage = (double) withoutDisadvantageTotal / iterations;
        double withDisadvantageAverage = (double) withDisadvantageTotal / iterations;

        assertThat(withDisadvantageAverage).isLessThan(withoutDisadvantageAverage - 2);
    }
}
