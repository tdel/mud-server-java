package fr.idev.mudserver.game;

import org.junit.jupiter.api.Test;

import fr.idev.mudserver.domain.CharacterClass;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClassServiceTest {

    @Test
    void warmClassHitDiceLoadsTheOfficial5eHitDieForEveryClass() {
        ClassService classService = new ClassService(new ObjectMapper());

        classService.warmClassHitDice();

        assertThat(classService.hitDie(CharacterClass.BARBARIAN)).isEqualTo(12);
        assertThat(classService.hitDie(CharacterClass.BARD)).isEqualTo(8);
        assertThat(classService.hitDie(CharacterClass.CLERIC)).isEqualTo(8);
        assertThat(classService.hitDie(CharacterClass.DRUID)).isEqualTo(8);
        assertThat(classService.hitDie(CharacterClass.FIGHTER)).isEqualTo(10);
        assertThat(classService.hitDie(CharacterClass.MONK)).isEqualTo(8);
        assertThat(classService.hitDie(CharacterClass.PALADIN)).isEqualTo(10);
        assertThat(classService.hitDie(CharacterClass.RANGER)).isEqualTo(10);
        assertThat(classService.hitDie(CharacterClass.ROGUE)).isEqualTo(8);
        assertThat(classService.hitDie(CharacterClass.SORCERER)).isEqualTo(6);
        assertThat(classService.hitDie(CharacterClass.WARLOCK)).isEqualTo(8);
        assertThat(classService.hitDie(CharacterClass.WIZARD)).isEqualTo(6);
    }

    @Test
    void hitDieThrowsBeforeWarmUp() {
        ClassService classService = new ClassService(new ObjectMapper());

        assertThatThrownBy(() -> classService.hitDie(CharacterClass.FIGHTER)).isInstanceOf(IllegalStateException.class);
    }
}
