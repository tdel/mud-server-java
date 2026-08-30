package app.game.combat;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import app.domain.item.ArmorCategory;

class CombatFormulasTest {

    @Test
    void statBonusIsNeutralAtScoreTen() {
        assertThat(CombatFormulas.statBonus(10)).isEqualTo(1.0);
        assertThat(CombatFormulas.statBonus(20)).isGreaterThan(1.0);
        assertThat(CombatFormulas.statBonus(1)).isLessThan(1.0);
    }

    @Test
    void higherAccuracyThanEvasionApproachesMaxHitChance() {
        assertThat(CombatFormulas.hitChance(1000, 1)).isEqualTo(CombatFormulas.MAX_HIT_CHANCE);
    }

    @Test
    void higherEvasionThanAccuracyApproachesMinHitChance() {
        assertThat(CombatFormulas.hitChance(1, 1000)).isEqualTo(CombatFormulas.MIN_HIT_CHANCE);
    }

    @Test
    void equalAccuracyAndEvasionGiveFiftyPercentHitChance() {
        assertThat(CombatFormulas.hitChance(50, 50)).isEqualTo(0.5);
    }

    @Test
    void criticalRateIsClampedToBounds() {
        assertThat(CombatFormulas.criticalRate(30, 1000)).isEqualTo(CombatFormulas.MAX_CRIT_RATE);
        assertThat(CombatFormulas.criticalRate(1, -1000)).isEqualTo(CombatFormulas.MIN_CRIT_RATE);
    }

    @Test
    void resolveDamageIsNeverBelowOne() {
        assertThat(CombatFormulas.resolveDamage(1, 100000, 1.0, false)).isEqualTo(1);
    }

    @Test
    void criticalHitDoublesDamageAtEqualVariance() {
        int normal = CombatFormulas.resolveDamage(50, 50, 1.0, false);
        int critical = CombatFormulas.resolveDamage(50, 50, 1.0, true);
        assertThat(critical).isEqualTo(normal * 2);
    }

    @Test
    void physicalAttackGrowsWithStrengthAndLevel() {
        int lowStrLowLevel = CombatFormulas.physicalAttack(10, 8, 1);
        int highStrLowLevel = CombatFormulas.physicalAttack(10, 18, 1);
        int lowStrHighLevel = CombatFormulas.physicalAttack(10, 8, 20);

        assertThat(highStrLowLevel).isGreaterThan(lowStrLowLevel);
        assertThat(lowStrHighLevel).isGreaterThan(lowStrLowLevel);
    }

    @Test
    void accuracyAndEvasionGrowWithDexterity() {
        int lowDex = CombatFormulas.accuracy(1, 8, 0);
        int highDex = CombatFormulas.accuracy(1, 18, 0);
        assertThat(highDex).isGreaterThan(lowDex);

        int lowDexEvasion = CombatFormulas.evasion(1, 8, 0, 0);
        int highDexEvasion = CombatFormulas.evasion(1, 18, 0, 0);
        assertThat(highDexEvasion).isGreaterThan(lowDexEvasion);
    }

    @Test
    void heavierArmorPenalizesEvasionMore() {
        assertThat(CombatFormulas.armorWeightPenalty(null)).isEqualTo(0);
        assertThat(CombatFormulas.armorWeightPenalty(ArmorCategory.LIGHT))
                .isGreaterThan(CombatFormulas.armorWeightPenalty(ArmorCategory.MEDIUM));
        assertThat(CombatFormulas.armorWeightPenalty(ArmorCategory.MEDIUM))
                .isGreaterThan(CombatFormulas.armorWeightPenalty(ArmorCategory.HEAVY));
    }

    @Test
    void physicalDefenseAndMagicalDefenseAreNeverBelowOne() {
        assertThat(CombatFormulas.physicalDefense(0, 1)).isGreaterThanOrEqualTo(1);
        assertThat(CombatFormulas.magicalDefense(0, 1)).isGreaterThanOrEqualTo(1);
    }
}
