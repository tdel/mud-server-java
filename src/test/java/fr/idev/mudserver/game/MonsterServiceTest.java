package fr.idev.mudserver.game;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import fr.idev.mudserver.domain.Attribute;
import fr.idev.mudserver.domain.GameMonster;
import fr.idev.mudserver.domain.Room;
import fr.idev.mudserver.domain.TestAttributes;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MonsterServiceTest {

    private static final UUID CLEARING_ID = UUID.fromString("7f55fd0c-23f8-4a3b-82a2-95a79bdbf2b5");

    @Test
    void warmMonstersLoadsTheRealCatalogFromJsonAndPlacesItInItsRoom() {
        MonsterService monsterService = new MonsterService(new ObjectMapper());
        Room clearing = new Room(CLEARING_ID, "Clairière", "...", null);

        monsterService.warmMonsters(List.of(clearing));

        assertThat(clearing.getMonsters()).hasSize(1);
        GameMonster goblin = clearing.getMonsters().get(0);
        assertThat(goblin.getName()).isEqualTo("Gobelin");
        assertThat(goblin.getMaxHealth()).isEqualTo(7);
        assertThat(goblin.getCurrentHealth()).isEqualTo(7);
        assertThat(goblin.getAttribute(Attribute.DEXTERITY)).isEqualTo(14);
        assertThat(goblin.getArmorClass()).isEqualTo(15);
        assertThat(goblin.getCurrentRoom()).isEqualTo(clearing);
        assertThat(goblin.getDescription()).isNotBlank();
    }

    @Test
    void loadMonstersThrowsWhenASpawnReferencesAnUnknownTemplate() {
        MonsterService isolated = new MonsterService(new ObjectMapper());
        MonsterService.MonsterFileDefinition file = new MonsterService.MonsterFileDefinition(List.of(), List.of(
                new MonsterService.MonsterSpawnDefinition(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())));

        assertThatThrownBy(() -> isolated.loadMonsters(file, List.of())).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void loadMonstersThrowsWhenASpawnReferencesAnUnknownRoom() {
        MonsterService isolated = new MonsterService(new ObjectMapper());
        UUID templateId = UUID.randomUUID();
        MonsterService.MonsterTemplateDefinition template = new MonsterService.MonsterTemplateDefinition(templateId,
                "Test", "...", 5, TestAttributes.of(10, 10, 10, 10, 10, 10), null);
        MonsterService.MonsterFileDefinition file = new MonsterService.MonsterFileDefinition(List.of(template),
                List.of(new MonsterService.MonsterSpawnDefinition(UUID.randomUUID(), templateId, UUID.randomUUID())));

        assertThatThrownBy(() -> isolated.loadMonsters(file, List.of())).isInstanceOf(IllegalStateException.class);
    }
}
