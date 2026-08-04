package fr.idev.mudserver.domain;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GameMonsterTest {

    @Test
    void armorClassUsesTheTemplateNaturalArmorClassWhenSet() {
        GameMonster monster = monster(TestAttributes.of(10, 14, 10, 10, 10, 10), 15);

        assertThat(monster.getArmorClass()).isEqualTo(15);
    }

    @Test
    void armorClassFallsBackToTenPlusDexModifierWhenTemplateHasNoNaturalArmorClass() {
        GameMonster monster = monster(TestAttributes.of(10, 14, 10, 10, 10, 10), null);

        assertThat(monster.getArmorClass()).isEqualTo(12);
    }

    private GameMonster monster(Map<Attribute, Integer> attributes, Integer naturalArmorClass) {
        MonsterTemplate template = new MonsterTemplate(UUID.randomUUID(), "Gobelin", "Une créature verte", 7,
                attributes, naturalArmorClass);
        GameMonster monster = new GameMonster(UUID.randomUUID(), template.getName(), template.getId(),
                UUID.randomUUID(), attributes, template.getMaxHealth());
        monster.attachTemplate(template);
        return monster;
    }
}
