package fr.idev.mudserver.game;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import fr.idev.mudserver.domain.actor.Attribute;
import fr.idev.mudserver.domain.actor.CharacterClass;
import fr.idev.mudserver.domain.actor.GamePlayer;
import fr.idev.mudserver.domain.actor.Gender;
import fr.idev.mudserver.domain.actor.Race;
import fr.idev.mudserver.domain.actor.Skill;
import fr.idev.mudserver.domain.actor.TestAttributes;
import fr.idev.mudserver.domain.actor.TestProficiencies;
import fr.idev.mudserver.game.dice.DiceRoller;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrairement à {@link CombatServiceTest}, pas de règle de critique sur 1/20
 * naturel ici (voir {@code CheckService} Javadoc) : {@code resolveCheck} est
 * une simple comparaison, donc les tests succès/échec garantis sont
 * déterministes avec une DC extrême, sans avoir besoin de retenter face au RNG
 * réel.
 */
class CheckServiceTest {

    private final CheckService checkService = new CheckService(new DiceRoller());

    @Test
    void resolveCheckSucceedsWhenTotalMeetsOrExceedsTheDc() {
        assertThat(CheckService.resolveCheck(15, 15)).isTrue();
        assertThat(CheckService.resolveCheck(16, 15)).isTrue();
    }

    @Test
    void resolveCheckFailsWhenTotalIsBelowTheDc() {
        assertThat(CheckService.resolveCheck(14, 15)).isFalse();
    }

    @Test
    void checkAppliesProficiencyBonusWhenTheClassIsProficientInTheSkill() {
        // FIGHTER est proficient en ATHLETICS (voir data/class.json) : mod FOR +3,
        // bonus de maîtrise niveau 1 = +2.
        GamePlayer fighter = player(CharacterClass.FIGHTER, 16, 1);

        for (int i = 0; i < 50; i++) {
            CheckResult result = checkService.check(fighter, Skill.ATHLETICS, 0);
            assertThat(result.proficient()).isTrue();
            assertThat(result.total()).isBetween(1 + 3 + 2, 20 + 3 + 2);
        }
    }

    @Test
    void checkDoesNotApplyProficiencyBonusWhenTheClassIsNotProficientInTheSkill() {
        // FIGHTER n'est pas proficient en STEALTH.
        GamePlayer fighter = player(CharacterClass.FIGHTER, 10, 1);

        for (int i = 0; i < 50; i++) {
            CheckResult result = checkService.check(fighter, Skill.STEALTH, 0);
            assertThat(result.proficient()).isFalse();
            assertThat(result.total()).isBetween(1, 20);
        }
    }

    @Test
    void saveAppliesProficiencyBonusWhenTheClassIsProficientInTheSavingThrow() {
        // FIGHTER est proficient en jets de sauvegarde de FOR.
        GamePlayer fighter = player(CharacterClass.FIGHTER, 16, 1);

        for (int i = 0; i < 50; i++) {
            CheckResult result = checkService.save(fighter, Attribute.STRENGTH, 0);
            assertThat(result.proficient()).isTrue();
            assertThat(result.total()).isBetween(1 + 3 + 2, 20 + 3 + 2);
        }
    }

    @Test
    void saveDoesNotApplyProficiencyBonusWhenTheClassIsNotProficientInTheSavingThrow() {
        // FIGHTER n'est pas proficient en jets de sauvegarde d'INT.
        GamePlayer fighter = player(CharacterClass.FIGHTER, 10, 1);

        for (int i = 0; i < 50; i++) {
            CheckResult result = checkService.save(fighter, Attribute.INTELLIGENCE, 0);
            assertThat(result.proficient()).isFalse();
            assertThat(result.total()).isBetween(1, 20);
        }
    }

    @Test
    void aTrivialDcAlwaysSucceeds() {
        GamePlayer fighter = player(CharacterClass.FIGHTER, 10, 1);

        assertThat(checkService.check(fighter, Skill.ATHLETICS, -100).success()).isTrue();
    }

    @Test
    void anImpossibleDcAlwaysFails() {
        GamePlayer fighter = player(CharacterClass.FIGHTER, 10, 1);

        assertThat(checkService.check(fighter, Skill.ATHLETICS, 9999).success()).isFalse();
    }

    private GamePlayer player(CharacterClass characterClass, int strength, int level) {
        return new GamePlayer(UUID.randomUUID(), UUID.randomUUID(), "Testeur", UUID.randomUUID(), Gender.MAN,
                Race.HUMAN, characterClass, TestProficiencies.savingThrows(characterClass),
                TestProficiencies.skills(characterClass), level, 10, 10,
                TestAttributes.of(strength, 10, 10, 10, 10, 10), 0, 0);
    }
}
