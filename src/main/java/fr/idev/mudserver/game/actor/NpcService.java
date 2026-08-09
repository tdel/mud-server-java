package fr.idev.mudserver.game.actor;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import fr.idev.mudserver.domain.actor.GameNpc;
import fr.idev.mudserver.domain.actor.GameNpc.NpcDialogueOptionType;
import fr.idev.mudserver.domain.actor.GameNpcSeller;
import fr.idev.mudserver.domain.HexCoordinate;
import fr.idev.mudserver.domain.Room;
import fr.idev.mudserver.game.ItemService;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Précharge les PNJ depuis {@code data/npcs.json}, sur le même principe que
 * {@code RoomService.warmRooms()} : contrairement à {@code MonsterService}, pas
 * de split template/instance ici — un NPC n'est qu'un nom et une room, rien à
 * dédupliquer entre instances — donc chaque entrée de {@code npcs.json} est
 * déjà une instance placée, comme {@code data/rooms.json}.
 *
 * <p>
 * {@code itemSummariesById} (paramètre de {@link #warmNpcs}, transmis par
 * {@code ServerApplication.warmupRunner} après {@code itemService
 * .warmItemTemplates()}) sert à valider au démarrage, fail-fast, le catalogue
 * boutique d'un PNJ marchand — même principe que
 * {@code MonsterService.loadMonsters} pour les tables de butin — et à
 * dénormaliser le nom et la rareté de chaque article sur
 * {@code GameNpcSeller.NpcShopEntry}, pour qu'il n'ait plus besoin d'une
 * dépendance à {@code ItemService} à l'exécution.
 */
@Service
public class NpcService {

    private static final Logger log = LoggerFactory.getLogger(NpcService.class);

    private static final String NPCS_RESOURCE = "/data/npcs.json";

    private final ObjectMapper objectMapper;

    public NpcService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void warmNpcs(Collection<Room> rooms, Map<UUID, ItemService.ItemSummary> itemSummariesById) {
        try (InputStream in = getClass().getResourceAsStream(NPCS_RESOURCE)) {
            List<NpcDefinition> definitions = objectMapper.readValue(in, new TypeReference<List<NpcDefinition>>() {
            });
            loadNpcs(definitions, rooms, itemSummariesById);
        } catch (IOException | JacksonException e) {
            throw new IllegalStateException("Impossible de charger " + NPCS_RESOURCE, e);
        }
    }

    void loadNpcs(List<NpcDefinition> definitions, Collection<Room> rooms,
            Map<UUID, ItemService.ItemSummary> itemSummariesById) {
        Map<UUID, Room> roomsById = new ConcurrentHashMap<>();
        for (Room room : rooms) {
            roomsById.put(room.getId(), room);
        }

        for (NpcDefinition definition : definitions) {
            Room room = roomsById.get(definition.roomId());
            if (room == null) {
                throw new IllegalStateException("NPC " + definition.id() + " référence la room " + definition.roomId()
                        + ", absente de data/rooms.json");
            }

            GameNpc.NpcDialogue dialogue = toDialogue(definition);
            GameNpcSeller.NpcShop shop = toShop(definition, itemSummariesById);

            GameNpc npc = shop != null
                    ? new GameNpcSeller(definition.id(), definition.name(), room.getId(), definition.description(),
                            dialogue, shop)
                    : new GameNpc(definition.id(), definition.name(), room.getId(), definition.description(), dialogue);
            npc.setCurrentRoom(room);
            room.placeNpc(npc, new HexCoordinate(definition.cell().q(), definition.cell().r()));
        }

        log.info("npc.instances_placed count={}", definitions.size());
    }

    private GameNpc.NpcDialogue toDialogue(NpcDefinition definition) {
        DialogueDefinition dialogueDef = definition.dialogue();
        if (dialogueDef == null) {
            return null;
        }

        List<GameNpc.NpcDialogueOption> options = dialogueDef.options().stream()
                .map(o -> new GameNpc.NpcDialogueOption(o.label(), o.type(), o.response())).toList();
        return new GameNpc.NpcDialogue(dialogueDef.greeting(), options);
    }

    private GameNpcSeller.NpcShop toShop(NpcDefinition definition,
            Map<UUID, ItemService.ItemSummary> itemSummariesById) {
        DialogueDefinition dialogueDef = definition.dialogue();
        if (dialogueDef == null) {
            return null;
        }

        boolean hasShopOption = dialogueDef.options().stream().anyMatch(o -> o.type() == NpcDialogueOptionType.SHOP);
        if (!hasShopOption) {
            return null;
        }

        ShopDefinition shopDef = dialogueDef.shop();
        if (shopDef == null || shopDef.items().isEmpty()) {
            throw new IllegalStateException("NPC " + definition.id()
                    + " a une option SHOP mais aucun catalogue \"shop\" dans " + NPCS_RESOURCE);
        }

        List<GameNpcSeller.NpcShopEntry> entries = new ArrayList<>();
        for (ShopEntryDefinition entry : shopDef.items()) {
            ItemService.ItemSummary summary = itemSummariesById.get(entry.itemTemplateId());
            if (summary == null) {
                throw new IllegalStateException("NPC " + definition.id() + " vend l'item " + entry.itemTemplateId()
                        + ", absent de data/items.json");
            }
            if (entry.price() <= 0) {
                throw new IllegalStateException("NPC " + definition.id() + " vend l'item " + entry.itemTemplateId()
                        + " à un prix invalide (" + entry.price() + ")");
            }
            entries.add(new GameNpcSeller.NpcShopEntry(entry.itemTemplateId(), summary.name(), summary.rarity(),
                    entry.price()));
        }
        return new GameNpcSeller.NpcShop(entries);
    }

    record NpcDefinition(UUID id, String name, UUID roomId, CellDefinition cell, String description,
            DialogueDefinition dialogue) {
    }

    record CellDefinition(int q, int r) {
    }

    record DialogueDefinition(String greeting, List<DialogueOptionDefinition> options, ShopDefinition shop) {
    }

    record DialogueOptionDefinition(String label, NpcDialogueOptionType type, String response) {
    }

    record ShopDefinition(List<ShopEntryDefinition> items) {
    }

    record ShopEntryDefinition(UUID itemTemplateId, int price) {
    }
}
