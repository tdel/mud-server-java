package fr.idev.mudserver.game.actor;

import java.util.Map;

import org.junit.jupiter.api.Test;

import fr.idev.mudserver.domain.actor.Attribute;
import fr.idev.mudserver.domain.actor.Race;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RaceServiceTest {

    @Test
    void warmRaceBonusesLoadsTheOfficial5eBonusesForEveryRace() {
        RaceService raceService = new RaceService(new ObjectMapper());

        raceService.warmRaceBonuses();

        assertThat(raceService.attributeScoreBonuses(Race.DWARF)).containsOnly(Map.entry(Attribute.CONSTITUTION, 2));
        assertThat(raceService.attributeScoreBonuses(Race.HUMAN)).containsOnly(Map.entry(Attribute.STRENGTH, 1),
                Map.entry(Attribute.DEXTERITY, 1), Map.entry(Attribute.CONSTITUTION, 1),
                Map.entry(Attribute.INTELLIGENCE, 1), Map.entry(Attribute.WISDOM, 1), Map.entry(Attribute.CHARISMA, 1));
        assertThat(raceService.attributeScoreBonuses(Race.HIGH_ELF)).containsOnly(Map.entry(Attribute.DEXTERITY, 2),
                Map.entry(Attribute.INTELLIGENCE, 1));
        assertThat(raceService.attributeScoreBonuses(Race.HALF_ORC)).containsOnly(Map.entry(Attribute.STRENGTH, 2),
                Map.entry(Attribute.CONSTITUTION, 1));
        assertThat(raceService.attributeScoreBonuses(Race.DRAGONBORN)).containsOnly(Map.entry(Attribute.STRENGTH, 2),
                Map.entry(Attribute.CHARISMA, 1));
        assertThat(raceService.attributeScoreBonuses(Race.ELF)).containsOnly(Map.entry(Attribute.DEXTERITY, 2));
        assertThat(raceService.attributeScoreBonuses(Race.GNOME)).containsOnly(Map.entry(Attribute.INTELLIGENCE, 2));
        assertThat(raceService.attributeScoreBonuses(Race.ROCK_GNOME))
                .containsOnly(Map.entry(Attribute.INTELLIGENCE, 2), Map.entry(Attribute.CONSTITUTION, 1));
        assertThat(raceService.attributeScoreBonuses(Race.HALF_ELF)).containsOnly(Map.entry(Attribute.CHARISMA, 2),
                Map.entry(Attribute.INTELLIGENCE, 1), Map.entry(Attribute.WISDOM, 1));
        assertThat(raceService.attributeScoreBonuses(Race.HALFLING)).containsOnly(Map.entry(Attribute.DEXTERITY, 2));
        assertThat(raceService.attributeScoreBonuses(Race.LIGHTFOOT_HALFLING))
                .containsOnly(Map.entry(Attribute.DEXTERITY, 2), Map.entry(Attribute.CHARISMA, 1));
        assertThat(raceService.attributeScoreBonuses(Race.TIEFLING)).containsOnly(Map.entry(Attribute.INTELLIGENCE, 1),
                Map.entry(Attribute.CHARISMA, 2));
        assertThat(raceService.attributeScoreBonuses(Race.HILL_DWARF))
                .containsOnly(Map.entry(Attribute.CONSTITUTION, 2), Map.entry(Attribute.WISDOM, 1));
    }

    @Test
    void warmRaceBonusesLoadsTheOfficial5eSpeedForEveryRace() {
        RaceService raceService = new RaceService(new ObjectMapper());

        raceService.warmRaceBonuses();

        assertThat(raceService.speed(Race.DWARF)).isEqualTo(5);
        assertThat(raceService.speed(Race.HUMAN)).isEqualTo(6);
        assertThat(raceService.speed(Race.HIGH_ELF)).isEqualTo(6);
        assertThat(raceService.speed(Race.HALF_ORC)).isEqualTo(6);
        assertThat(raceService.speed(Race.DRAGONBORN)).isEqualTo(6);
        assertThat(raceService.speed(Race.ELF)).isEqualTo(6);
        assertThat(raceService.speed(Race.GNOME)).isEqualTo(5);
        assertThat(raceService.speed(Race.ROCK_GNOME)).isEqualTo(5);
        assertThat(raceService.speed(Race.HALF_ELF)).isEqualTo(6);
        assertThat(raceService.speed(Race.HALFLING)).isEqualTo(5);
        assertThat(raceService.speed(Race.LIGHTFOOT_HALFLING)).isEqualTo(5);
        assertThat(raceService.speed(Race.TIEFLING)).isEqualTo(6);
        assertThat(raceService.speed(Race.HILL_DWARF)).isEqualTo(5);
    }

    @Test
    void attributeScoreBonusesThrowsBeforeWarmUp() {
        RaceService raceService = new RaceService(new ObjectMapper());

        assertThatThrownBy(() -> raceService.attributeScoreBonuses(Race.HUMAN))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void speedThrowsBeforeWarmUp() {
        RaceService raceService = new RaceService(new ObjectMapper());

        assertThatThrownBy(() -> raceService.speed(Race.HUMAN)).isInstanceOf(IllegalStateException.class);
    }
}
