package fr.idev.mudserver.game.actor;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import fr.idev.mudserver.domain.actor.Attribute;
import fr.idev.mudserver.domain.actor.GameMonster;
import fr.idev.mudserver.domain.Room;
import fr.idev.mudserver.domain.actor.TestAttributes;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MonsterServiceTest {

    private static final UUID VILLAGE_SQUARE_ID = UUID.fromString("5e4ada37-37e1-438c-9233-581f10c055c7");
    private static final UUID FOREST_EDGE_ID = UUID.fromString("9a884ac7-b954-4cd6-ab67-c677d472cb0f");
    private static final UUID TAVERN_ID = UUID.fromString("e1da77bd-f0b3-4d5a-95da-e8765c4fc973");
    private static final UUID CLEARING_ID = UUID.fromString("7f55fd0c-23f8-4a3b-82a2-95a79bdbf2b5");
    private static final UUID CEMETERY_PATH_ID = UUID.fromString("4dae9974-45f7-46c9-8e66-12cdac759860");

    @Test
    void warmMonstersLoadsTheRealCatalogFromJsonAndPlacesItInItsRoom() {
        MonsterService monsterService = new MonsterService(new ObjectMapper());
        Room villageSquare = new Room(VILLAGE_SQUARE_ID, "Place du village", "...", null);
        Room forestEdge = new Room(FOREST_EDGE_ID, "Orée de la forêt", "...", null);
        Room tavern = new Room(TAVERN_ID, "Taverne du Sanglier Roux", "...", null);
        Room clearing = new Room(CLEARING_ID, "Clairière", "...", null);
        Room cemeteryPath = new Room(CEMETERY_PATH_ID, "Chemin du cimetière", "...", null);

        monsterService.warmMonsters(List.of(villageSquare, forestEdge, tavern, clearing, cemeteryPath));

        assertThat(villageSquare.getMonsters()).hasSize(0);
        assertThat(forestEdge.getMonsters()).hasSize(2);
        assertThat(tavern.getMonsters()).hasSize(1);
        assertThat(clearing.getMonsters()).hasSize(3);
        assertThat(cemeteryPath.getMonsters()).hasSize(1);
        GameMonster goblin = clearing.getMonsters().get(0);
        assertThat(goblin.getName()).isEqualTo("Gobelin");
        assertThat(goblin.getMaxHealth()).isEqualTo(7);
        assertThat(goblin.getCurrentHealth()).isEqualTo(7);
        assertThat(goblin.getAttribute(Attribute.DEXTERITY)).isEqualTo(14);
        assertThat(goblin.getArmorClass()).isEqualTo(15);
        assertThat(goblin.getCurrentRoom()).isEqualTo(clearing);
        assertThat(goblin.getDescription()).isNotBlank();
        assertThat(goblin.getTemplate().getXpReward()).isEqualTo(50);
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
                "Test", "...", 5, TestAttributes.of(10, 10, 10, 10, 10, 10), null, 0, "1d4");
        MonsterService.MonsterFileDefinition file = new MonsterService.MonsterFileDefinition(List.of(template),
                List.of(new MonsterService.MonsterSpawnDefinition(UUID.randomUUID(), templateId, UUID.randomUUID())));

        assertThatThrownBy(() -> isolated.loadMonsters(file, List.of())).isInstanceOf(IllegalStateException.class);
    }
}
