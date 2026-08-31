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
    void magicCriticalRateIsClampedToBounds() {
        assertThat(CombatFormulas.magicCriticalRate(30, 1000)).isEqualTo(CombatFormulas.MAX_CRIT_RATE);
        assertThat(CombatFormulas.magicCriticalRate(1, -1000)).isEqualTo(CombatFormulas.MIN_CRIT_RATE);
    }

    @Test
    void magicCriticalRateGrowsWithWit() {
        int lowWit = CombatFormulas.magicCriticalRate(8, 0);
        int highWit = CombatFormulas.magicCriticalRate(18, 0);
        assertThat(highWit).isGreaterThan(lowWit);
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

    @Test
    void maxHealthGrowsWithConstitutionAndLevel() {
        int lowConLowLevel = CombatFormulas.maxHealth(10, 8, 1);
        int highConLowLevel = CombatFormulas.maxHealth(10, 18, 1);
        int lowConHighLevel = CombatFormulas.maxHealth(10, 8, 20);

        assertThat(highConLowLevel).isGreaterThan(lowConLowLevel);
        assertThat(lowConHighLevel).isGreaterThan(lowConLowLevel);
        assertThat(CombatFormulas.maxHealth(10, 1, 1)).isGreaterThanOrEqualTo(1);
    }

    @Test
    void maxManaGrowsWithMenAndLevel() {
        int lowMenLowLevel = CombatFormulas.maxMana(15, 8, 1);
        int highMenLowLevel = CombatFormulas.maxMana(15, 18, 1);
        int lowMenHighLevel = CombatFormulas.maxMana(15, 8, 20);

        assertThat(highMenLowLevel).isGreaterThan(lowMenLowLevel);
        assertThat(lowMenHighLevel).isGreaterThan(lowMenLowLevel);
        assertThat(CombatFormulas.maxMana(0, 10, 1)).isGreaterThanOrEqualTo(0);
    }

    @Test
    void healthAndManaRegenPerTickAreNeverBelowOneAndGrowWithScore() {
        assertThat(CombatFormulas.healthRegenPerTick(1, 1)).isGreaterThanOrEqualTo(1);
        assertThat(CombatFormulas.manaRegenPerTick(1, 1)).isGreaterThanOrEqualTo(1);

        int lowCon = CombatFormulas.healthRegenPerTick(1000, 8);
        int highCon = CombatFormulas.healthRegenPerTick(1000, 18);
        assertThat(highCon).isGreaterThan(lowCon);

        int lowMen = CombatFormulas.manaRegenPerTick(1000, 8);
        int highMen = CombatFormulas.manaRegenPerTick(1000, 18);
        assertThat(highMen).isGreaterThan(lowMen);
    }

    @Test
    void applyElementalResistanceReducesDamageWithPositiveResist() {
        int reduced = CombatFormulas.applyElementalResistance(100, 20);
        assertThat(reduced).isLessThan(100);
    }

    @Test
    void applyElementalResistanceAmplifiesDamageWithNegativeResist() {
        int amplified = CombatFormulas.applyElementalResistance(100, -20);
        assertThat(amplified).isGreaterThan(100);
    }

    @Test
    void applyElementalResistanceIsClampedToMultiplierBounds() {
        assertThat(CombatFormulas.applyElementalResistance(100, 1000)).isEqualTo(10);
        assertThat(CombatFormulas.applyElementalResistance(100, -1000)).isEqualTo(200);
    }

    @Test
    void applyElementalResistanceNeverBelowOne() {
        assertThat(CombatFormulas.applyElementalResistance(1, 90)).isEqualTo(1);
    }

    @Test
    void enchantBonusScalesLinearly() {
        int unenchanted = CombatFormulas.enchantBonus(10, 0, CombatFormulas.ENCHANT_ATK_BONUS_PER_LEVEL);
        int enchantedPlusFive = CombatFormulas.enchantBonus(10, 5, CombatFormulas.ENCHANT_ATK_BONUS_PER_LEVEL);
        assertThat(unenchanted).isEqualTo(10);
        assertThat(enchantedPlusFive).isEqualTo(10 + 5 * CombatFormulas.ENCHANT_ATK_BONUS_PER_LEVEL);
    }

    @Test
    void enchantBonusLeavesZeroBaseStatUnaffected() {
        assertThat(CombatFormulas.enchantBonus(0, 10, CombatFormulas.ENCHANT_ATK_BONUS_PER_LEVEL)).isZero();
    }

    @Test
    void debuffResistChanceGrowsWithMen() {
        double lowMen = CombatFormulas.debuffResistChance(8);
        double highMen = CombatFormulas.debuffResistChance(18);
        assertThat(highMen).isGreaterThan(lowMen);
    }

    @Test
    void debuffResistChanceClampedToMax() {
        assertThat(CombatFormulas.debuffResistChance(200)).isEqualTo(CombatFormulas.MAX_DEBUFF_RESIST / 100.0);
    }
}
