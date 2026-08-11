package fr.idev.mudserver.game.actor;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import fr.idev.mudserver.domain.HexCoordinate;
import fr.idev.mudserver.domain.RoomInstance;
import fr.idev.mudserver.domain.WorldTemplate;
import fr.idev.mudserver.domain.actor.GameNpc;
import fr.idev.mudserver.domain.actor.GameNpcSeller;
import fr.idev.mudserver.domain.actor.NpcTemplate;
import fr.idev.mudserver.game.ItemService;
import fr.idev.mudserver.game.WorldTemplateService;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NpcServiceTest {

    private static final UUID TAVERN_ID = UUID.fromString("e1da77bd-f0b3-4d5a-95da-e8765c4fc973");
    private static final UUID VILLAGE_SQUARE_ID = UUID.fromString("5e4ada37-37e1-438c-9233-581f10c055c7");

    /**
     * Même principe que {@code MonsterServiceTest#realItemTemplateIds()} : reste
     * synchronisée avec {@code data/items.json} sans dépendre d'un contexte Spring
     * dans ce test unitaire pur.
     */
    private static Map<UUID, ItemService.ItemSummary> realItemTemplateSummariesById() {
        ItemService itemService = new ItemService(null, new ObjectMapper());
        itemService.warmItemTemplates();
        return itemService.templateSummariesById();
    }

    /**
     * Le contenu statique (dialogue/boutique déjà résolus) vient désormais de
     * {@link WorldTemplateService}, pas de {@code NpcService} lui-même — voir sa
     * Javadoc.
     */
    private static WorldTemplate defaultWorldTemplate() {
        WorldTemplateService service = new WorldTemplateService(new ObjectMapper(),
                new PathMatchingResourcePatternResolver());
        service.warmWorldTemplates(realItemTemplateSummariesById());
        return service.findByShortName("default").orElseThrow();
    }

    @Test
    void warmNpcsPlacesEachNpcTemplateInItsRoom() {
        WorldTemplate worldTemplate = defaultWorldTemplate();
        RoomInstance tavern = new RoomInstance(TAVERN_ID, "Taverne du Sanglier Roux", "...", null);
        RoomInstance villageSquare = new RoomInstance(VILLAGE_SQUARE_ID, "Place du village", "...", null);

        new NpcService().warmNpcs(List.of(worldTemplate), List.of(tavern, villageSquare));

        assertThat(tavern.getNpcs()).hasSize(1);
        GameNpc innkeeper = tavern.getNpcs().get(0);
        assertThat(innkeeper.getName()).isEqualTo("Aubergiste");
        assertThat(innkeeper.getCurrentRoom()).isEqualTo(tavern);
        assertThat(innkeeper.getDescription()).isNotBlank();
        assertThat(innkeeper.getDialogue()).isPresent();
        assertThat(innkeeper.getDialogue().get().options()).isNotEmpty();
        assertThat(innkeeper).isInstanceOf(GameNpcSeller.class);
        assertThat(((GameNpcSeller) innkeeper).shop().items()).isNotEmpty();

        assertThat(villageSquare.getNpcs()).hasSize(1);
        GameNpc guard = villageSquare.getNpcs().get(0);
        assertThat(guard.getName()).isEqualTo("Garde du village");
        assertThat(guard.getCurrentRoom()).isEqualTo(villageSquare);
        assertThat(guard.getDescription()).isNotBlank();
        assertThat(guard.getDialogue()).isEmpty();
        assertThat(guard).isNotInstanceOf(GameNpcSeller.class);
    }

    @Test
    void warmNpcsThrowsWhenAnNpcTemplateReferencesARoomNotInTheSuppliedCollection() {
        UUID roomTemplateId = UUID.randomUUID();
        NpcTemplate npcTemplate = new NpcTemplate(UUID.randomUUID(), "Test", roomTemplateId, new HexCoordinate(0, 0),
                "...", null, null);
        WorldTemplate worldTemplate = new WorldTemplate(UUID.randomUUID(), "test", "Test", "...", 1, 1, Map.of(),
                Map.of(npcTemplate.id(), npcTemplate));

        assertThatThrownBy(() -> new NpcService().warmNpcs(List.of(worldTemplate), List.of()))
                .isInstanceOf(IllegalStateException.class);
    }
}
