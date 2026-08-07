package fr.idev.mudserver.game.actor;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import fr.idev.mudserver.domain.actor.GameNpc;
import fr.idev.mudserver.domain.actor.GameNpc.NpcDialogueOptionType;
import fr.idev.mudserver.domain.actor.GameNpcSeller;
import fr.idev.mudserver.domain.Room;
import fr.idev.mudserver.game.ItemService;
import fr.idev.mudserver.game.actor.NpcService.DialogueDefinition;
import fr.idev.mudserver.game.actor.NpcService.DialogueOptionDefinition;
import fr.idev.mudserver.game.actor.NpcService.ShopDefinition;
import fr.idev.mudserver.game.actor.NpcService.ShopEntryDefinition;
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
    private static Map<UUID, String> realItemTemplateNamesById() {
        ItemService itemService = new ItemService(null, new ObjectMapper());
        itemService.warmItemTemplates();
        return itemService.templateNamesById();
    }

    @Test
    void warmNpcsLoadsTheRealCatalogFromJsonAndPlacesItInItsRoom() {
        NpcService npcService = new NpcService(new ObjectMapper());
        Room tavern = new Room(TAVERN_ID, "Taverne du Sanglier Roux", "...", null);
        Room villageSquare = new Room(VILLAGE_SQUARE_ID, "Place du village", "...", null);

        npcService.warmNpcs(List.of(tavern, villageSquare), realItemTemplateNamesById());

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
    void loadNpcsThrowsWhenAnNpcReferencesAnUnknownRoom() {
        NpcService isolated = new NpcService(new ObjectMapper());
        List<NpcService.NpcDefinition> definitions = List.of(new NpcService.NpcDefinition(UUID.randomUUID(), "Test",
                UUID.randomUUID(), new NpcService.CellDefinition(0, 0), "...", null));

        assertThatThrownBy(() -> isolated.loadNpcs(definitions, List.of(), Map.of()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void loadNpcsThrowsWhenAShopOptionHasNoShopCatalog() {
        NpcService isolated = new NpcService(new ObjectMapper());
        Room room = new Room(UUID.randomUUID(), "Test", "...", null);
        DialogueDefinition dialogue = new DialogueDefinition("Salut",
                List.of(new DialogueOptionDefinition("Voir la boutique", NpcDialogueOptionType.SHOP, null)), null);
        List<NpcService.NpcDefinition> definitions = List.of(new NpcService.NpcDefinition(UUID.randomUUID(), "Test",
                room.getId(), new NpcService.CellDefinition(0, 0), "...", dialogue));

        assertThatThrownBy(() -> isolated.loadNpcs(definitions, List.of(room), Map.of()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void loadNpcsThrowsWhenAShopEntryReferencesAnUnknownItemTemplate() {
        NpcService isolated = new NpcService(new ObjectMapper());
        Room room = new Room(UUID.randomUUID(), "Test", "...", null);
        ShopDefinition shop = new ShopDefinition(List.of(new ShopEntryDefinition(UUID.randomUUID(), 10)));
        DialogueDefinition dialogue = new DialogueDefinition("Salut",
                List.of(new DialogueOptionDefinition("Voir la boutique", NpcDialogueOptionType.SHOP, null)), shop);
        List<NpcService.NpcDefinition> definitions = List.of(new NpcService.NpcDefinition(UUID.randomUUID(), "Test",
                room.getId(), new NpcService.CellDefinition(0, 0), "...", dialogue));

        assertThatThrownBy(() -> isolated.loadNpcs(definitions, List.of(room), Map.of()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void loadNpcsThrowsWhenAShopEntryHasAnInvalidPrice() {
        NpcService isolated = new NpcService(new ObjectMapper());
        Room room = new Room(UUID.randomUUID(), "Test", "...", null);
        UUID itemTemplateId = UUID.randomUUID();
        ShopDefinition shop = new ShopDefinition(List.of(new ShopEntryDefinition(itemTemplateId, 0)));
        DialogueDefinition dialogue = new DialogueDefinition("Salut",
                List.of(new DialogueOptionDefinition("Voir la boutique", NpcDialogueOptionType.SHOP, null)), shop);
        List<NpcService.NpcDefinition> definitions = List.of(new NpcService.NpcDefinition(UUID.randomUUID(), "Test",
                room.getId(), new NpcService.CellDefinition(0, 0), "...", dialogue));

        assertThatThrownBy(() -> isolated.loadNpcs(definitions, List.of(room), Map.of(itemTemplateId, "Potion")))
                .isInstanceOf(IllegalStateException.class);
    }
}
