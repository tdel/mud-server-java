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

        assertThat(raceService.attributeScoreBonuses(Race.DWARF)).containsExactly(Map.entry(Attribute.STRENGTH, 2),
                Map.entry(Attribute.CONSTITUTION, 2));
        assertThat(raceService.attributeScoreBonuses(Race.HUMAN)).containsExactly(Map.entry(Attribute.STRENGTH, 1),
                Map.entry(Attribute.DEXTERITY, 1), Map.entry(Attribute.CONSTITUTION, 1),
                Map.entry(Attribute.INTELLIGENCE, 1), Map.entry(Attribute.WISDOM, 1), Map.entry(Attribute.CHARISMA, 1));
        assertThat(raceService.attributeScoreBonuses(Race.HIGH_ELF)).containsExactly(Map.entry(Attribute.DEXTERITY, 2),
                Map.entry(Attribute.INTELLIGENCE, 2), Map.entry(Attribute.WISDOM, 1), Map.entry(Attribute.CHARISMA, 1));
        assertThat(raceService.attributeScoreBonuses(Race.ORC)).containsExactly(Map.entry(Attribute.STRENGTH, 2),
                Map.entry(Attribute.DEXTERITY, 1), Map.entry(Attribute.WISDOM, 1));
    }

    @Test
    void attributeScoreBonusesThrowsBeforeWarmUp() {
        RaceService raceService = new RaceService(new ObjectMapper());

        assertThatThrownBy(() -> raceService.attributeScoreBonuses(Race.HUMAN))
                .isInstanceOf(IllegalStateException.class);
    }
}
