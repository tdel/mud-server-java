package fr.idev.mudserver.game.actor;

import org.junit.jupiter.api.Test;

import fr.idev.mudserver.domain.actor.CharacterClass;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClassServiceTest {

    @Test
    void warmClassDefinitionsLoadsTheOfficial5eHitDieForEveryClass() {
        ClassService classService = new ClassService(new ObjectMapper());

        classService.warmClassDefinitions();

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

    @Test
    void warmClassDefinitionsLoadsTheOfficial5eStartingGoldForEveryClass() {
        ClassService classService = new ClassService(new ObjectMapper());

        classService.warmClassDefinitions();

        assertThat(classService.startingGold(CharacterClass.BARBARIAN))
                .isEqualTo(new ClassService.StartingGold("2d4", 10));
        assertThat(classService.startingGold(CharacterClass.MONK)).isEqualTo(new ClassService.StartingGold("5d4", 1));
        assertThat(classService.startingGold(CharacterClass.WIZARD))
                .isEqualTo(new ClassService.StartingGold("4d4", 10));
    }

    @Test
    void startingGoldThrowsBeforeWarmUp() {
        ClassService classService = new ClassService(new ObjectMapper());

        assertThatThrownBy(() -> classService.startingGold(CharacterClass.FIGHTER))
                .isInstanceOf(IllegalStateException.class);
    }
}
