package fr.idev.mudserver.game.actor;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LevelServiceTest {

    @Test
    void warmXpThresholdsLoadsTheOfficial5eExperienceTableForEveryLevel() {
        LevelService levelService = new LevelService(new ObjectMapper());

        levelService.warmXpThresholds();

        assertThat(levelService.xpRequiredForLevel(1)).isEqualTo(0);
        assertThat(levelService.xpRequiredForLevel(2)).isEqualTo(300);
        assertThat(levelService.xpRequiredForLevel(3)).isEqualTo(900);
        assertThat(levelService.xpRequiredForLevel(4)).isEqualTo(2700);
        assertThat(levelService.xpRequiredForLevel(5)).isEqualTo(6500);
        assertThat(levelService.xpRequiredForLevel(6)).isEqualTo(14000);
        assertThat(levelService.xpRequiredForLevel(7)).isEqualTo(23000);
        assertThat(levelService.xpRequiredForLevel(8)).isEqualTo(34000);
        assertThat(levelService.xpRequiredForLevel(9)).isEqualTo(48000);
        assertThat(levelService.xpRequiredForLevel(10)).isEqualTo(64000);
        assertThat(levelService.xpRequiredForLevel(11)).isEqualTo(85000);
        assertThat(levelService.xpRequiredForLevel(12)).isEqualTo(100000);
        assertThat(levelService.xpRequiredForLevel(13)).isEqualTo(120000);
        assertThat(levelService.xpRequiredForLevel(14)).isEqualTo(140000);
        assertThat(levelService.xpRequiredForLevel(15)).isEqualTo(165000);
        assertThat(levelService.xpRequiredForLevel(16)).isEqualTo(195000);
        assertThat(levelService.xpRequiredForLevel(17)).isEqualTo(225000);
        assertThat(levelService.xpRequiredForLevel(18)).isEqualTo(265000);
        assertThat(levelService.xpRequiredForLevel(19)).isEqualTo(305000);
        assertThat(levelService.xpRequiredForLevel(20)).isEqualTo(355000);
    }

    @Test
    void xpRequiredForLevelThrowsBeforeWarmUp() {
        LevelService levelService = new LevelService(new ObjectMapper());

        assertThatThrownBy(() -> levelService.xpRequiredForLevel(1)).isInstanceOf(IllegalStateException.class);
    }
}
