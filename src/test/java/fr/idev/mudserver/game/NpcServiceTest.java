package fr.idev.mudserver.game;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import fr.idev.mudserver.domain.GameNpc;
import fr.idev.mudserver.domain.Room;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NpcServiceTest {

    private static final UUID TAVERN_ID = UUID.fromString("e1da77bd-f0b3-4d5a-95da-e8765c4fc973");
    private static final UUID VILLAGE_SQUARE_ID = UUID.fromString("5e4ada37-37e1-438c-9233-581f10c055c7");

    @Test
    void warmNpcsLoadsTheRealCatalogFromJsonAndPlacesItInItsRoom() {
        NpcService npcService = new NpcService(new ObjectMapper());
        Room tavern = new Room(TAVERN_ID, "Taverne du Sanglier Roux", "...", null);
        Room villageSquare = new Room(VILLAGE_SQUARE_ID, "Place du village", "...", null);

        npcService.warmNpcs(List.of(tavern, villageSquare));

        assertThat(tavern.getNpcs()).hasSize(1);
        GameNpc innkeeper = tavern.getNpcs().get(0);
        assertThat(innkeeper.getName()).isEqualTo("Aubergiste");
        assertThat(innkeeper.getCurrentRoom()).isEqualTo(tavern);

        assertThat(villageSquare.getNpcs()).hasSize(1);
        GameNpc guard = villageSquare.getNpcs().get(0);
        assertThat(guard.getName()).isEqualTo("Garde du village");
        assertThat(guard.getCurrentRoom()).isEqualTo(villageSquare);
    }

    @Test
    void loadNpcsThrowsWhenAnNpcReferencesAnUnknownRoom() {
        NpcService isolated = new NpcService(new ObjectMapper());
        List<NpcService.NpcDefinition> definitions = List
                .of(new NpcService.NpcDefinition(UUID.randomUUID(), "Test", UUID.randomUUID()));

        assertThatThrownBy(() -> isolated.loadNpcs(definitions, List.of())).isInstanceOf(IllegalStateException.class);
    }
}
