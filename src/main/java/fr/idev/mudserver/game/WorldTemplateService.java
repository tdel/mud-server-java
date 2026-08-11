package fr.idev.mudserver.game;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Service;

import fr.idev.mudserver.domain.HexCoordinate;
import fr.idev.mudserver.domain.MonsterSpawn;
import fr.idev.mudserver.domain.RoomTemplate;
import fr.idev.mudserver.domain.RoomTemplatePortal;
import fr.idev.mudserver.domain.WorldTemplate;
import fr.idev.mudserver.domain.actor.GameNpc;
import fr.idev.mudserver.domain.actor.GameNpc.NpcDialogueOptionType;
import fr.idev.mudserver.domain.actor.GameNpcSeller;
import fr.idev.mudserver.domain.actor.NpcTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Point d'entrée unique pour le contenu statique d'un World : rooms (avec leurs
 * points de spawn de monstres) et PNJ, un {@link WorldTemplate} par dossier
 * sous {@code data/worlds/}. Chaque dossier {@code data/worlds/{shortName}/}
 * porte un {@code world.json} (métadonnées : id, nom, description, min/max
 * joueurs) plus son propre {@code rooms.json}/ {@code npcs.json} — même format
 * que les anciens {@code data/rooms.json}/ {@code data/npcs.json} qu'ils
 * remplacent, juste un jeu de fichiers par monde plutôt qu'un seul global.
 * {@code data/items.json}/
 * {@code data/monsters.json}/{@code data/race.json}/{@code data/class.json}/
 * {@code data/levels.json} restent globaux, partagés entre tous les
 * {@link WorldTemplate} — hors périmètre de cette classe.
 *
 * <p>
 * L'énumération des dossiers de {@code data/worlds/} passe par
 * {@link ResourcePatternResolver} plutôt que
 * {@code getClass().getResourceAsStream(...)} (utilisé partout ailleurs dans ce
 * codebase pour un fichier connu à l'avance) : lister le contenu d'un dossier
 * classpath ne fonctionne pas une fois l'application packagée en jar
 * exécutable, alors que résoudre un pattern avec wildcard (
 * {@code classpath*:data/worlds/*&#47;world.json}) fonctionne aussi bien depuis
 * un jar que depuis {@code target/classes} en dev/test.
 */
@Service
public class WorldTemplateService {

    private static final Logger log = LoggerFactory.getLogger(WorldTemplateService.class);

    private static final String WORLDS_MANIFEST_PATTERN = "classpath*:data/worlds/*/world.json";
    private static final String WORLDS_DIR_MARKER = "data/worlds/";

    private final Map<UUID, WorldTemplate> templatesById = new ConcurrentHashMap<>();
    private final Map<String, UUID> idByShortName = new ConcurrentHashMap<>();

    private final ObjectMapper objectMapper;
    private final ResourcePatternResolver resourcePatternResolver;

    public WorldTemplateService(ObjectMapper objectMapper, ResourcePatternResolver resourcePatternResolver) {
        this.objectMapper = objectMapper;
        this.resourcePatternResolver = resourcePatternResolver;
    }

    /**
     * Recharge intégralement l'ensemble des mondes à chaque appel (jamais un ajout
     * incrémental) : comme {@code ItemService.warmItemTemplates()}, sûr à rappeler
     * plusieurs fois (un contexte Spring de test est mis en cache et partagé entre
     * classes de test, chacune pouvant déclencher son propre warmup) — la détection
     * de doublon d'id ne porte donc que sur le lot chargé dans <em>cet</em> appel,
     * pas sur un état accumulé entre appels.
     */
    public void warmWorldTemplates(Map<UUID, ItemService.ItemSummary> itemSummariesById) {
        Resource[] manifests;
        try {
            manifests = resourcePatternResolver.getResources(WORLDS_MANIFEST_PATTERN);
        } catch (IOException e) {
            throw new IllegalStateException("Impossible d'énumérer " + WORLDS_MANIFEST_PATTERN, e);
        }
        if (manifests.length == 0) {
            throw new IllegalStateException("Aucun monde trouvé sous " + WORLDS_MANIFEST_PATTERN);
        }

        Map<UUID, WorldTemplate> loaded = new LinkedHashMap<>();
        Map<String, UUID> loadedByShortName = new LinkedHashMap<>();
        for (Resource manifest : manifests) {
            String shortName = shortNameOf(manifest);
            WorldTemplate template = loadWorldTemplate(shortName, itemSummariesById);
            if (loaded.putIfAbsent(template.getId(), template) != null) {
                throw new IllegalStateException(
                        "Le monde " + shortName + " a un id " + template.getId() + " déjà utilisé par un autre monde");
            }
            loadedByShortName.put(shortName, template.getId());
        }

        templatesById.clear();
        templatesById.putAll(loaded);
        idByShortName.clear();
        idByShortName.putAll(loadedByShortName);
        log.info("world.templates_loaded count={}", templatesById.size());
    }

    private String shortNameOf(Resource manifest) {
        String uri;
        try {
            uri = manifest.getURI().toString();
        } catch (IOException e) {
            throw new IllegalStateException("Impossible de résoudre l'URI de " + manifest, e);
        }
        int markerIndex = uri.indexOf(WORLDS_DIR_MARKER);
        if (markerIndex == -1) {
            throw new IllegalStateException("Ressource " + uri + " hors de " + WORLDS_DIR_MARKER);
        }
        String rest = uri.substring(markerIndex + WORLDS_DIR_MARKER.length());
        int slashIndex = rest.indexOf('/');
        if (slashIndex == -1) {
            throw new IllegalStateException("Impossible d'extraire le nom court du monde depuis " + uri);
        }
        return rest.substring(0, slashIndex);
    }

    private WorldTemplate loadWorldTemplate(String shortName, Map<UUID, ItemService.ItemSummary> itemSummariesById) {
        WorldManifestDefinition manifest = readJson(shortName, "world.json", WorldManifestDefinition.class);
        if (manifest.minPlayers() < 1) {
            throw new IllegalStateException(
                    "Le monde " + shortName + " a un minPlayers invalide (" + manifest.minPlayers() + ")");
        }
        if (manifest.maxPlayers() < manifest.minPlayers()) {
            throw new IllegalStateException("Le monde " + shortName + " a un maxPlayers (" + manifest.maxPlayers()
                    + ") inférieur à son minPlayers (" + manifest.minPlayers() + ")");
        }

        List<RoomDefinition> roomDefinitions = readJsonList(shortName, "rooms.json",
                new TypeReference<List<RoomDefinition>>() {
                });
        Map<UUID, RoomTemplate> roomTemplates = buildRoomTemplates(shortName, roomDefinitions);

        List<NpcDefinition> npcDefinitions = readJsonList(shortName, "npcs.json",
                new TypeReference<List<NpcDefinition>>() {
                });
        Map<UUID, NpcTemplate> npcTemplates = buildNpcTemplates(shortName, npcDefinitions, roomTemplates,
                itemSummariesById);

        WorldTemplate template = new WorldTemplate(manifest.id(), shortName, manifest.name(), manifest.description(),
                manifest.minPlayers(), manifest.maxPlayers(), roomTemplates, npcTemplates);

        log.info("world.template_loaded shortName={} id={} rooms={} npcs={}", shortName, template.getId(),
                roomTemplates.size(), npcTemplates.size());
        return template;
    }

    Map<UUID, RoomTemplate> buildRoomTemplates(String shortName, List<RoomDefinition> definitions) {
        long startingRoomCount = definitions.stream().filter(d -> Boolean.TRUE.equals(d.isStartingRoom())).count();
        if (startingRoomCount != 1) {
            throw new IllegalStateException("Le monde " + shortName + " doit avoir exactement une room isStartingRoom"
                    + " (trouvé " + startingRoomCount + ")");
        }

        Map<UUID, RoomTemplate> templates = new LinkedHashMap<>();
        for (RoomDefinition definition : definitions) {
            HexCoordinate spawnCell = new HexCoordinate(definition.spawnCell().q(), definition.spawnCell().r());
            if (definition.width() <= 0 || definition.height() <= 0) {
                throw new IllegalStateException("Room " + definition.id() + " du monde " + shortName
                        + " a une grille invalide (" + definition.width() + "x" + definition.height() + ")");
            }
            RoomTemplate template = new RoomTemplate(definition.id(), definition.name(), definition.description(),
                    definition.isStartingRoom(), definition.width(), definition.height(), spawnCell,
                    definition.monsterSpawns().stream().map(spawn -> new MonsterSpawn(spawn.id(), spawn.templateId(),
                            new HexCoordinate(spawn.cell().q(), spawn.cell().r()))).toList());
            if (!template.isInBounds(spawnCell)) {
                throw new IllegalStateException("Room " + definition.id() + " du monde " + shortName
                        + " a une case de spawn " + spawnCell + " hors des bornes de sa grille (" + definition.width()
                        + "x" + definition.height() + ")");
            }
            if (templates.putIfAbsent(template.getId(), template) != null) {
                throw new IllegalStateException("Room " + definition.id() + " dupliquée dans le monde " + shortName);
            }
        }

        for (RoomDefinition definition : definitions) {
            RoomTemplate source = templates.get(definition.id());
            List<RoomTemplatePortal> portals = definition.portals().stream()
                    .map(portal -> resolvePortal(shortName, definition, source, portal, templates)).toList();
            checkNoDuplicatePortalCell(shortName, definition, portals);
            source.setPortals(portals);
        }

        return Map.copyOf(templates);
    }

    private RoomTemplatePortal resolvePortal(String shortName, RoomDefinition definition, RoomTemplate source,
            PortalDefinition portal, Map<UUID, RoomTemplate> templates) {
        RoomTemplate target = templates.get(portal.targetRoomId());
        if (target == null) {
            throw new IllegalStateException("Room " + definition.id() + " du monde " + shortName + " a un portail '"
                    + portal.direction() + "' vers " + portal.targetRoomId() + ", absente de ce monde");
        }

        HexCoordinate cell = new HexCoordinate(portal.cell().q(), portal.cell().r());
        if (!source.isBorderCell(cell)) {
            throw new IllegalStateException("Room " + definition.id() + " du monde " + shortName + " a un portail en "
                    + cell + " hors des bords de sa grille (" + source.getWidth() + "x" + source.getHeight() + ")");
        }

        HexCoordinate targetCell = new HexCoordinate(portal.targetCell().q(), portal.targetCell().r());
        if (!target.isInBounds(targetCell)) {
            throw new IllegalStateException("Room " + definition.id() + " du monde " + shortName + " a un portail vers "
                    + targetCell + " hors des bornes de la grille de la room cible " + portal.targetRoomId() + " ("
                    + target.getWidth() + "x" + target.getHeight() + ")");
        }

        return new RoomTemplatePortal(cell, portal.direction(), target.getId(), targetCell);
    }

    private void checkNoDuplicatePortalCell(String shortName, RoomDefinition definition,
            List<RoomTemplatePortal> portals) {
        long distinctCells = portals.stream().map(RoomTemplatePortal::cell).distinct().count();
        if (distinctCells != portals.size()) {
            throw new IllegalStateException(
                    "Room " + definition.id() + " du monde " + shortName + " a plusieurs portails sur la même case");
        }
    }

    Map<UUID, NpcTemplate> buildNpcTemplates(String shortName, List<NpcDefinition> definitions,
            Map<UUID, RoomTemplate> roomTemplates, Map<UUID, ItemService.ItemSummary> itemSummariesById) {
        Map<UUID, NpcTemplate> templates = new LinkedHashMap<>();
        for (NpcDefinition definition : definitions) {
            RoomTemplate room = roomTemplates.get(definition.roomId());
            if (room == null) {
                throw new IllegalStateException("NPC " + definition.id() + " du monde " + shortName
                        + " référence la room " + definition.roomId() + ", absente de ce monde");
            }

            GameNpc.NpcDialogue dialogue = toDialogue(definition);
            GameNpcSeller.NpcShop shop = toShop(shortName, definition, itemSummariesById);

            NpcTemplate template = new NpcTemplate(definition.id(), definition.name(), definition.roomId(),
                    new HexCoordinate(definition.cell().q(), definition.cell().r()), definition.description(), dialogue,
                    shop);
            if (templates.putIfAbsent(template.id(), template) != null) {
                throw new IllegalStateException("NPC " + definition.id() + " dupliqué dans le monde " + shortName);
            }
        }
        return Map.copyOf(templates);
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

    private GameNpcSeller.NpcShop toShop(String shortName, NpcDefinition definition,
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
            throw new IllegalStateException("NPC " + definition.id() + " du monde " + shortName
                    + " a une option SHOP mais aucun catalogue \"shop\"");
        }

        List<GameNpcSeller.NpcShopEntry> entries = new ArrayList<>();
        for (ShopEntryDefinition entry : shopDef.items()) {
            ItemService.ItemSummary summary = itemSummariesById.get(entry.itemTemplateId());
            if (summary == null) {
                throw new IllegalStateException("NPC " + definition.id() + " du monde " + shortName + " vend l'item "
                        + entry.itemTemplateId() + ", absent de data/items.json");
            }
            if (entry.price() <= 0) {
                throw new IllegalStateException("NPC " + definition.id() + " du monde " + shortName + " vend l'item "
                        + entry.itemTemplateId() + " à un prix invalide (" + entry.price() + ")");
            }
            entries.add(new GameNpcSeller.NpcShopEntry(entry.itemTemplateId(), summary.name(), summary.rarity(),
                    entry.price()));
        }
        return new GameNpcSeller.NpcShop(entries);
    }

    private <T> T readJson(String shortName, String fileName, Class<T> type) {
        try (InputStream in = worldFile(shortName, fileName).getInputStream()) {
            return objectMapper.readValue(in, type);
        } catch (IOException | JacksonException e) {
            throw new IllegalStateException("Impossible de charger " + WORLDS_DIR_MARKER + shortName + "/" + fileName,
                    e);
        }
    }

    private <T> T readJsonList(String shortName, String fileName, TypeReference<T> typeReference) {
        try (InputStream in = worldFile(shortName, fileName).getInputStream()) {
            return objectMapper.readValue(in, typeReference);
        } catch (IOException | JacksonException e) {
            throw new IllegalStateException("Impossible de charger " + WORLDS_DIR_MARKER + shortName + "/" + fileName,
                    e);
        }
    }

    private Resource worldFile(String shortName, String fileName) {
        return resourcePatternResolver.getResource("classpath:" + WORLDS_DIR_MARKER + shortName + "/" + fileName);
    }

    public Collection<WorldTemplate> allTemplates() {
        return templatesById.values();
    }

    public Optional<WorldTemplate> findById(UUID id) {
        return Optional.ofNullable(templatesById.get(id));
    }

    public Optional<WorldTemplate> findByShortName(String shortName) {
        UUID id = idByShortName.get(shortName);
        return id == null ? Optional.empty() : findById(id);
    }

    record WorldManifestDefinition(UUID id, String name, String description, int minPlayers, int maxPlayers) {
    }

    record RoomDefinition(UUID id, String name, String description, Boolean isStartingRoom, int width, int height,
            CellDefinition spawnCell, List<PortalDefinition> portals, List<MonsterSpawnDefinition> monsterSpawns) {
    }

    record CellDefinition(int q, int r) {
    }

    record MonsterSpawnDefinition(UUID id, UUID templateId, CellDefinition cell) {
    }

    record PortalDefinition(CellDefinition cell, String direction, UUID targetRoomId, CellDefinition targetCell) {
    }

    record NpcDefinition(UUID id, String name, UUID roomId, CellDefinition cell, String description,
            DialogueDefinition dialogue) {
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
