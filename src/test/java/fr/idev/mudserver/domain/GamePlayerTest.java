package fr.idev.mudserver.domain;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GamePlayerTest {

    @Test
    void modifierRoundsDownForOddScores() {
        GamePlayer character = character(7, 10, 12, 20, 10, 10, 1);

        assertThat(character.getModifier(Attribute.STRENGTH)).isEqualTo(-2);
        assertThat(character.getModifier(Attribute.DEXTERITY)).isEqualTo(0);
        assertThat(character.getModifier(Attribute.CONSTITUTION)).isEqualTo(1);
        assertThat(character.getModifier(Attribute.INTELLIGENCE)).isEqualTo(5);
    }

    @Test
    void proficiencyBonusFollowsTheLevelTiers() {
        assertThat(character(10, 10, 10, 10, 10, 10, 1).getProficiencyBonus()).isEqualTo(2);
        assertThat(character(10, 10, 10, 10, 10, 10, 4).getProficiencyBonus()).isEqualTo(2);
        assertThat(character(10, 10, 10, 10, 10, 10, 5).getProficiencyBonus()).isEqualTo(3);
        assertThat(character(10, 10, 10, 10, 10, 10, 8).getProficiencyBonus()).isEqualTo(3);
        assertThat(character(10, 10, 10, 10, 10, 10, 9).getProficiencyBonus()).isEqualTo(4);
        assertThat(character(10, 10, 10, 10, 10, 10, 20).getProficiencyBonus()).isEqualTo(6);
    }

    private GamePlayer character(int strength, int dexterity, int constitution, int intelligence, int wisdom,
            int charisma, int level) {
        return new GamePlayer(UUID.randomUUID(), UUID.randomUUID(), "Test", UUID.randomUUID(), Race.HUMAN,
                CharacterClass.FIGHTER, level, 10, 10,
                TestAttributes.of(strength, dexterity, constitution, intelligence, wisdom, charisma));
    }
}
