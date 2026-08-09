package fr.idev.mudserver.game.dice;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import fr.idev.mudserver.domain.ArmorCategory;
import fr.idev.mudserver.domain.EquipmentSlot;
import fr.idev.mudserver.domain.Item;
import fr.idev.mudserver.domain.ItemTemplate;
import fr.idev.mudserver.domain.ItemType;
import fr.idev.mudserver.domain.Rarity;
import fr.idev.mudserver.domain.actor.Attribute;
import fr.idev.mudserver.domain.actor.CharacterClass;
import fr.idev.mudserver.domain.actor.GamePlayer;
import fr.idev.mudserver.domain.actor.Gender;
import fr.idev.mudserver.domain.actor.Race;
import fr.idev.mudserver.domain.actor.Skill;
import fr.idev.mudserver.domain.actor.TestAttributes;
import fr.idev.mudserver.domain.actor.TestProficiencies;

import static org.assertj.core.api.Assertions.assertThat;

class DiceRollerTest {

    private final DiceRoller diceRoller = new DiceRoller();

    @Test
    void rollD20WithoutDisadvantageStaysWithinTheD20Range() {
        int modifier = 3;
        for (int i = 0; i < 1000; i++) {
            DiceRoll roll = diceRoller.rollD20(modifier, false);

            assertThat(roll.rolls()).hasSize(1);
            assertThat(roll.total()).isBetween(1 + modifier, 20 + modifier);
        }
    }

    @Test
    void rollD20WithDisadvantageStaysWithinTheD20RangeAndNeverDoubleCounts() {
        int modifier = 3;
        for (int i = 0; i < 1000; i++) {
            DiceRoll roll = diceRoller.rollD20(modifier, true);

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
            withoutDisadvantageTotal += diceRoller.rollD20(0, false).total();
            withDisadvantageTotal += diceRoller.rollD20(0, true).total();
        }

        double withoutDisadvantageAverage = (double) withoutDisadvantageTotal / iterations;
        double withDisadvantageAverage = (double) withDisadvantageTotal / iterations;

        assertThat(withDisadvantageAverage).isLessThan(withoutDisadvantageAverage - 2);
    }

    /**
     * Contrairement à {@link fr.idev.mudserver.game.CombatServiceTest}, pas de
     * règle de critique sur 1/20 naturel ici (voir {@link DiceRoller#check}
     * Javadoc) : {@code resolveCheck} est une simple comparaison, donc les tests
     * succès/échec garantis sont déterministes avec une DC extrême, sans avoir
     * besoin de retenter face au RNG réel.
     */
    @Test
    void resolveCheckSucceedsWhenTotalMeetsOrExceedsTheDc() {
        assertThat(DiceRoller.resolveCheck(15, 15)).isTrue();
        assertThat(DiceRoller.resolveCheck(16, 15)).isTrue();
    }

    @Test
    void resolveCheckFailsWhenTotalIsBelowTheDc() {
        assertThat(DiceRoller.resolveCheck(14, 15)).isFalse();
    }

    @Test
    void checkAppliesProficiencyBonusWhenTheClassIsProficientInTheSkill() {
        // FIGHTER est proficient en ATHLETICS (voir data/class.json) : mod FOR +3,
        // bonus de maîtrise niveau 1 = +2.
        GamePlayer fighter = player(CharacterClass.FIGHTER, 16, 1);

        for (int i = 0; i < 50; i++) {
            CheckResult result = diceRoller.check(fighter, Skill.ATHLETICS, 0);
            assertThat(result.proficient()).isTrue();
            assertThat(result.total()).isBetween(1 + 3 + 2, 20 + 3 + 2);
        }
    }

    @Test
    void checkDoesNotApplyProficiencyBonusWhenTheClassIsNotProficientInTheSkill() {
        // FIGHTER n'est pas proficient en STEALTH.
        GamePlayer fighter = player(CharacterClass.FIGHTER, 10, 1);

        for (int i = 0; i < 50; i++) {
            CheckResult result = diceRoller.check(fighter, Skill.STEALTH, 0);
            assertThat(result.proficient()).isFalse();
            assertThat(result.total()).isBetween(1, 20);
        }
    }

    @Test
    void saveAppliesProficiencyBonusWhenTheClassIsProficientInTheSavingThrow() {
        // FIGHTER est proficient en jets de sauvegarde de FOR.
        GamePlayer fighter = player(CharacterClass.FIGHTER, 16, 1);

        for (int i = 0; i < 50; i++) {
            CheckResult result = diceRoller.save(fighter, Attribute.STRENGTH, 0);
            assertThat(result.proficient()).isTrue();
            assertThat(result.total()).isBetween(1 + 3 + 2, 20 + 3 + 2);
        }
    }

    @Test
    void saveDoesNotApplyProficiencyBonusWhenTheClassIsNotProficientInTheSavingThrow() {
        // FIGHTER n'est pas proficient en jets de sauvegarde d'INT.
        GamePlayer fighter = player(CharacterClass.FIGHTER, 10, 1);

        for (int i = 0; i < 50; i++) {
            CheckResult result = diceRoller.save(fighter, Attribute.INTELLIGENCE, 0);
            assertThat(result.proficient()).isFalse();
            assertThat(result.total()).isBetween(1, 20);
        }
    }

    @Test
    void aTrivialDcAlwaysSucceeds() {
        GamePlayer fighter = player(CharacterClass.FIGHTER, 10, 1);

        assertThat(diceRoller.check(fighter, Skill.ATHLETICS, -100).success()).isTrue();
    }

    @Test
    void anImpossibleDcAlwaysFails() {
        GamePlayer fighter = player(CharacterClass.FIGHTER, 10, 1);

        assertThat(diceRoller.check(fighter, Skill.ATHLETICS, 9999).success()).isFalse();
    }

    @Test
    void checkAppliesDisadvantageOnADexterityBasedSkillWhenWearingNonProficientArmor() {
        // WIZARD n'a aucune maîtrise d'armure ; DEX 10 => mod 0, pas de maîtrise sur
        // STEALTH non plus.
        GamePlayer wizard = player(CharacterClass.WIZARD, 10, 1);
        equipArmor(wizard, ArmorCategory.LIGHT);

        // Désavantage (2d20 garde le plus bas, moyenne ≈ 6.86) : nettement sous la
        // moyenne sans désavantage (10.5).
        double average = averageCheckTotal(wizard, Skill.STEALTH, 2000);
        assertThat(average).isBetween(5.8, 7.9);
    }

    @Test
    void checkDoesNotApplyDisadvantageOnAWisdomBasedSkillRegardlessOfArmor() {
        // PERCEPTION est gouvernée par la SAGESSE, pas la DEX/FOR : le désavantage
        // d'armure ne doit pas s'appliquer.
        GamePlayer wizard = player(CharacterClass.WIZARD, 10, 1);
        equipArmor(wizard, ArmorCategory.LIGHT);

        double average = averageCheckTotal(wizard, Skill.PERCEPTION, 2000);
        assertThat(average).isBetween(9.5, 11.5);
    }

    @Test
    void saveAppliesDisadvantageOnADexteritySavingThrowWhenWearingNonProficientArmor() {
        GamePlayer wizard = player(CharacterClass.WIZARD, 10, 1);
        equipArmor(wizard, ArmorCategory.LIGHT);

        double average = averageSaveTotal(wizard, Attribute.DEXTERITY, 2000);
        assertThat(average).isBetween(5.8, 7.9);
    }

    private double averageCheckTotal(GamePlayer character, Skill skill, int iterations) {
        long total = 0;
        for (int i = 0; i < iterations; i++) {
            total += diceRoller.check(character, skill, 0).total();
        }
        return (double) total / iterations;
    }

    private double averageSaveTotal(GamePlayer character, Attribute attribute, int iterations) {
        long total = 0;
        for (int i = 0; i < iterations; i++) {
            total += diceRoller.save(character, attribute, 0).total();
        }
        return (double) total / iterations;
    }

    private void equipArmor(GamePlayer character, ArmorCategory armorCategory) {
        ItemTemplate template = new ItemTemplate(UUID.randomUUID(), "Armure", null, ItemType.ARMOR, 5, armorCategory,
                11, null, null, 0, Rarity.COMMON, 0);
        Item item = new Item(UUID.randomUUID(), template.getId(), null, null, EquipmentSlot.CHEST);
        item.attachTemplate(template);
        character.getInventory().addItem(item);
    }

    private GamePlayer player(CharacterClass characterClass, int strength, int level) {
        return new GamePlayer(UUID.randomUUID(), UUID.randomUUID(), "Testeur", UUID.randomUUID(), Gender.MAN,
                Race.HUMAN, characterClass, TestProficiencies.primaryAbility(characterClass),
                TestProficiencies.savingThrows(characterClass), TestProficiencies.skills(characterClass),
                TestProficiencies.weaponProficiencies(characterClass),
                TestProficiencies.armorProficiencies(characterClass), level, 10, 10,
                TestAttributes.of(strength, 10, 10, 10, 10, 10), 0, 0);
    }
}
