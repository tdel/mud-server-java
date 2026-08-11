package fr.idev.mudserver.game;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import fr.idev.mudserver.domain.HexCoordinate;
import fr.idev.mudserver.domain.MonsterSpawn;
import fr.idev.mudserver.domain.RoomTemplate;
import fr.idev.mudserver.domain.RoomTemplatePortal;
import fr.idev.mudserver.domain.WorldTemplate;
import fr.idev.mudserver.domain.actor.GameNpc.NpcDialogueOptionType;
import fr.idev.mudserver.domain.actor.GameNpcSeller;
import fr.idev.mudserver.domain.actor.NpcTemplate;
import fr.idev.mudserver.domain.Rarity;
import fr.idev.mudserver.game.WorldTemplateService.CellDefinition;
import fr.idev.mudserver.game.WorldTemplateService.DialogueDefinition;
import fr.idev.mudserver.game.WorldTemplateService.DialogueOptionDefinition;
import fr.idev.mudserver.game.WorldTemplateService.MonsterSpawnDefinition;
import fr.idev.mudserver.game.WorldTemplateService.NpcDefinition;
import fr.idev.mudserver.game.WorldTemplateService.PortalDefinition;
import fr.idev.mudserver.game.WorldTemplateService.RoomDefinition;
import fr.idev.mudserver.game.WorldTemplateService.ShopDefinition;
import fr.idev.mudserver.game.WorldTemplateService.ShopEntryDefinition;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

class WorldTemplateServiceTest {

    private static final UUID VILLAGE_SQUARE_ID = UUID.fromString("5e4ada37-37e1-438c-9233-581f10c055c7");
    private static final UUID FOREST_EDGE_ID = UUID.fromString("9a884ac7-b954-4cd6-ab67-c677d472cb0f");
    private static final UUID TAVERN_ID = UUID.fromString("e1da77bd-f0b3-4d5a-95da-e8765c4fc973");

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

    private static WorldTemplateService newService() {
        return new WorldTemplateService(new ObjectMapper(), new PathMatchingResourcePatternResolver());
    }

    @Test
    void warmWorldTemplatesLoadsTheRealCatalogFromJson() {
        WorldTemplateService service = newService();

        service.warmWorldTemplates(realItemTemplateSummariesById());

        WorldTemplate defaultWorld = service.findByShortName("default").orElseThrow();
        assertThat(defaultWorld.getMinPlayers()).isEqualTo(1);
        assertThat(defaultWorld.getMaxPlayers()).isGreaterThanOrEqualTo(1);

        RoomTemplate villageSquare = defaultWorld.getRoomTemplates().get(VILLAGE_SQUARE_ID);
        assertThat(villageSquare.getName()).isEqualTo("Place du village");
        assertThat(villageSquare.isStartingRoom()).isTrue();
        assertThat(villageSquare.getPortals()).extracting(RoomTemplatePortal::targetRoomTemplateId)
                .contains(FOREST_EDGE_ID);
        assertThat(defaultWorld.startingRoomTemplate()).map(RoomTemplate::getId).contains(VILLAGE_SQUARE_ID);

        NpcTemplate innkeeper = defaultWorld.getNpcTemplates().values().stream()
                .filter(npc -> npc.roomTemplateId().equals(TAVERN_ID)).findFirst().orElseThrow();
        assertThat(innkeeper.name()).isEqualTo("Aubergiste");
        assertThat(innkeeper.dialogue()).isNotNull();
        assertThat(innkeeper.shop()).isNotNull();
        assertThat(innkeeper.shop().items()).isNotEmpty();
    }

    @Test
    void buildRoomTemplatesThrowsWhenMoreThanOneRoomIsMarkedAsStarting() {
        WorldTemplateService service = newService();
        List<RoomDefinition> definitions = List.of(
                new RoomDefinition(UUID.randomUUID(), "A", "...", true, 16, 8, new CellDefinition(8, 4), List.of(),
                        List.of()),
                new RoomDefinition(UUID.randomUUID(), "B", "...", true, 16, 8, new CellDefinition(8, 4), List.of(),
                        List.of()));

        assertThatThrownBy(() -> service.buildRoomTemplates("test", definitions))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void buildRoomTemplatesThrowsWhenNoRoomIsMarkedAsStarting() {
        WorldTemplateService service = newService();
        List<RoomDefinition> definitions = List.of(new RoomDefinition(UUID.randomUUID(), "A", "...", null, 16, 8,
                new CellDefinition(8, 4), List.of(), List.of()));

        assertThatThrownBy(() -> service.buildRoomTemplates("test", definitions))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void buildRoomTemplatesThrowsWhenAPortalTargetsAnUnknownRoom() {
        WorldTemplateService service = newService();
        List<RoomDefinition> definitions = List.of(new RoomDefinition(UUID.randomUUID(), "A", "...", true, 16, 8,
                new CellDefinition(8, 4), List.of(new PortalDefinition(new CellDefinition(15, 4), "E",
                        UUID.randomUUID(), new CellDefinition(0, 4))),
                List.of()));

        assertThatThrownBy(() -> service.buildRoomTemplates("test", definitions))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void buildRoomTemplatesThrowsWhenAPortalCellIsNotOnTheBorder() {
        WorldTemplateService service = newService();
        UUID sourceId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        List<RoomDefinition> definitions = List.of(
                new RoomDefinition(sourceId, "Source", "...", true, 16, 8, new CellDefinition(8, 4),
                        List.of(new PortalDefinition(new CellDefinition(8, 4), "E", targetId,
                                new CellDefinition(0, 4))),
                        List.of()),
                new RoomDefinition(targetId, "Target", "...", null, 16, 8, new CellDefinition(8, 4), List.of(),
                        List.of()));

        assertThatThrownBy(() -> service.buildRoomTemplates("test", definitions))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void buildRoomTemplatesThrowsWhenAPortalTargetCellIsOutOfBoundsInTheTargetRoom() {
        WorldTemplateService service = newService();
        UUID sourceId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        List<RoomDefinition> definitions = List.of(
                new RoomDefinition(sourceId, "Source", "...", true, 16, 8, new CellDefinition(8, 4),
                        List.of(new PortalDefinition(new CellDefinition(15, 4), "E", targetId,
                                new CellDefinition(99, 99))),
                        List.of()),
                new RoomDefinition(targetId, "Target", "...", null, 16, 8, new CellDefinition(8, 4), List.of(),
                        List.of()));

        assertThatThrownBy(() -> service.buildRoomTemplates("test", definitions))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void buildRoomTemplatesThrowsWhenTwoPortalsShareTheSameCell() {
        WorldTemplateService service = newService();
        UUID sourceId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        List<RoomDefinition> definitions = List.of(new RoomDefinition(sourceId, "Source", "...", true, 16, 8,
                new CellDefinition(8, 4),
                List.of(new PortalDefinition(new CellDefinition(15, 4), "E", targetId, new CellDefinition(0, 4)),
                        new PortalDefinition(new CellDefinition(15, 4), "E", targetId, new CellDefinition(0, 5))),
                List.of()),
                new RoomDefinition(targetId, "Target", "...", null, 16, 8, new CellDefinition(8, 4), List.of(),
                        List.of()));

        assertThatThrownBy(() -> service.buildRoomTemplates("test", definitions))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void buildRoomTemplatesResolvesPortalsToTheTargetRoomTemplateId() {
        WorldTemplateService service = newService();
        UUID sourceId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        List<RoomDefinition> definitions = List.of(
                new RoomDefinition(sourceId, "Source", "...", true, 16, 8, new CellDefinition(8, 4),
                        List.of(new PortalDefinition(new CellDefinition(15, 4), "E", targetId,
                                new CellDefinition(0, 4))),
                        List.of()),
                new RoomDefinition(targetId, "Target", "...", null, 16, 8, new CellDefinition(8, 4), List.of(),
                        List.of()));

        Map<UUID, RoomTemplate> templates = service.buildRoomTemplates("test", definitions);

        RoomTemplate source = templates.get(sourceId);
        assertThat(source.getPortals()).extracting(RoomTemplatePortal::targetRoomTemplateId).containsExactly(targetId);
    }

    @Test
    void buildRoomTemplatesResolvesMonsterSpawnsOnTheTemplate() {
        WorldTemplateService service = newService();
        UUID roomId = UUID.randomUUID();
        UUID spawnId = UUID.randomUUID();
        UUID templateId = UUID.randomUUID();
        List<RoomDefinition> definitions = List
                .of(new RoomDefinition(roomId, "RoomInstance", "...", true, 16, 8, new CellDefinition(8, 4), List.of(),
                        List.of(new MonsterSpawnDefinition(spawnId, templateId, new CellDefinition(4, 2)))));

        Map<UUID, RoomTemplate> templates = service.buildRoomTemplates("test", definitions);

        assertThat(templates.get(roomId).getMonsterSpawns()).extracting(MonsterSpawn::id, MonsterSpawn::templateId)
                .containsExactly(tuple(spawnId, templateId));
    }

    @Test
    void buildNpcTemplatesThrowsWhenAnNpcReferencesAnUnknownRoom() {
        WorldTemplateService service = newService();
        List<NpcDefinition> definitions = List.of(
                new NpcDefinition(UUID.randomUUID(), "Test", UUID.randomUUID(), new CellDefinition(0, 0), "...", null));

        assertThatThrownBy(() -> service.buildNpcTemplates("test", definitions, Map.of(), Map.of()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void buildNpcTemplatesThrowsWhenAShopOptionHasNoShopCatalog() {
        WorldTemplateService service = newService();
        UUID roomId = UUID.randomUUID();
        DialogueDefinition dialogue = new DialogueDefinition("Salut",
                List.of(new DialogueOptionDefinition("Voir la boutique", NpcDialogueOptionType.SHOP, null)), null);
        List<NpcDefinition> definitions = List
                .of(new NpcDefinition(UUID.randomUUID(), "Test", roomId, new CellDefinition(0, 0), "...", dialogue));
        Map<UUID, RoomTemplate> roomTemplates = Map.of(roomId, aRoomTemplate(roomId));

        assertThatThrownBy(() -> service.buildNpcTemplates("test", definitions, roomTemplates, Map.of()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void buildNpcTemplatesThrowsWhenAShopEntryReferencesAnUnknownItemTemplate() {
        WorldTemplateService service = newService();
        UUID roomId = UUID.randomUUID();
        ShopDefinition shop = new ShopDefinition(List.of(new ShopEntryDefinition(UUID.randomUUID(), 10)));
        DialogueDefinition dialogue = new DialogueDefinition("Salut",
                List.of(new DialogueOptionDefinition("Voir la boutique", NpcDialogueOptionType.SHOP, null)), shop);
        List<NpcDefinition> definitions = List
                .of(new NpcDefinition(UUID.randomUUID(), "Test", roomId, new CellDefinition(0, 0), "...", dialogue));
        Map<UUID, RoomTemplate> roomTemplates = Map.of(roomId, aRoomTemplate(roomId));

        assertThatThrownBy(() -> service.buildNpcTemplates("test", definitions, roomTemplates, Map.of()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void buildNpcTemplatesThrowsWhenAShopEntryHasAnInvalidPrice() {
        WorldTemplateService service = newService();
        UUID roomId = UUID.randomUUID();
        UUID itemTemplateId = UUID.randomUUID();
        ShopDefinition shop = new ShopDefinition(List.of(new ShopEntryDefinition(itemTemplateId, 0)));
        DialogueDefinition dialogue = new DialogueDefinition("Salut",
                List.of(new DialogueOptionDefinition("Voir la boutique", NpcDialogueOptionType.SHOP, null)), shop);
        List<NpcDefinition> definitions = List
                .of(new NpcDefinition(UUID.randomUUID(), "Test", roomId, new CellDefinition(0, 0), "...", dialogue));
        Map<UUID, RoomTemplate> roomTemplates = Map.of(roomId, aRoomTemplate(roomId));
        Map<UUID, ItemService.ItemSummary> itemSummaries = Map.of(itemTemplateId,
                new ItemService.ItemSummary("Potion", Rarity.COMMON));

        assertThatThrownBy(() -> service.buildNpcTemplates("test", definitions, roomTemplates, itemSummaries))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void buildNpcTemplatesResolvesDialogueAndShopFromDefinitions() {
        WorldTemplateService service = newService();
        UUID roomId = UUID.randomUUID();
        UUID itemTemplateId = UUID.randomUUID();
        ShopDefinition shop = new ShopDefinition(List.of(new ShopEntryDefinition(itemTemplateId, 10)));
        DialogueDefinition dialogue = new DialogueDefinition("Salut",
                List.of(new DialogueOptionDefinition("Voir la boutique", NpcDialogueOptionType.SHOP, null)), shop);
        List<NpcDefinition> definitions = List
                .of(new NpcDefinition(UUID.randomUUID(), "Test", roomId, new CellDefinition(0, 0), "...", dialogue));
        Map<UUID, RoomTemplate> roomTemplates = Map.of(roomId, aRoomTemplate(roomId));
        Map<UUID, ItemService.ItemSummary> itemSummaries = Map.of(itemTemplateId,
                new ItemService.ItemSummary("Potion", Rarity.COMMON));

        Map<UUID, NpcTemplate> templates = service.buildNpcTemplates("test", definitions, roomTemplates, itemSummaries);

        NpcTemplate template = templates.values().iterator().next();
        assertThat(template.dialogue().greeting()).isEqualTo("Salut");
        assertThat(template.shop().items()).extracting(GameNpcSeller.NpcShopEntry::itemName).containsExactly("Potion");
    }

    private static RoomTemplate aRoomTemplate(UUID id) {
        return new RoomTemplate(id, "Test", "...", null, 16, 8, new HexCoordinate(8, 4), List.of());
    }
}
