package app.game.catalog;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Service;

import app.domain.NpcSpawn;
import app.domain.item.ItemTemplate;
import app.domain.world.MapTemplate;
import app.domain.world.MapTemplatePortal;
import app.domain.world.PeaceZone;
import app.domain.world.WorldTemplate;
import app.domain.world.WorldTemplateSummary;
import app.domain.actor.AbstractNpc;
import app.domain.actor.AbstractNpc.NpcDialogueOptionType;
import app.domain.actor.instance.NpcSellerInstance;
import app.domain.actor.template.NpcTemplate;
import app.game.catalog.tiled.TiledMap;
import app.game.catalog.tiled.TiledMapLoader;
import app.game.catalog.tiled.TiledMapLoader.ParsedMap;
import app.game.catalog.tiled.TiledMapLoader.PortalDraft;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
public class WorldTemplateCatalog {

    private static final Logger log = LoggerFactory.getLogger(WorldTemplateCatalog.class);

    private static final String WORLD_MANIFEST_PATTERN = "classpath*:data/world.json";
    private static final String DATA_DIR = "data/";
    private static final String DEFAULT_SHORT_NAME = "default";

    private final Map<UUID, WorldTemplateSummary> summariesById = new ConcurrentHashMap<>();
    private final Map<UUID, WorldTemplate> loadedTemplatesById = new ConcurrentHashMap<>();

    private final ObjectMapper objectMapper;
    private final ResourcePatternResolver resourcePatternResolver;
    private final ItemTemplateCatalog itemTemplateCatalog;

    public WorldTemplateCatalog(ObjectMapper objectMapper, ResourcePatternResolver resourcePatternResolver,
            ItemTemplateCatalog itemTemplateCatalog) {
        this.objectMapper = objectMapper;
        this.resourcePatternResolver = resourcePatternResolver;
        this.itemTemplateCatalog = itemTemplateCatalog;
    }

    public void warmWorldTemplates() {
        Resource[] manifests;
        try {
            manifests = resourcePatternResolver.getResources(WORLD_MANIFEST_PATTERN);
        } catch (IOException e) {
            throw new IllegalStateException("Impossible d'énumérer " + WORLD_MANIFEST_PATTERN, e);
        }
        if (manifests.length != 1) {
            throw new IllegalStateException("Un seul monde est supporté (retour au monde unique) : " + manifests.length
                    + " trouvé(s) sous " + WORLD_MANIFEST_PATTERN);
        }

        WorldTemplateSummary summary = loadSummary(DEFAULT_SHORT_NAME);
        summariesById.clear();
        summariesById.put(summary.id(), summary);
        log.info("world.templates_loaded count={}", summariesById.size());
    }

    private WorldTemplateSummary loadSummary(String shortName) {
        WorldManifestDefinition manifest = readJson("world.json", WorldManifestDefinition.class);
        if (manifest.minPlayers() < 1) {
            throw new IllegalStateException(
                    "Le monde " + shortName + " a un minPlayers invalide (" + manifest.minPlayers() + ")");
        }
        if (manifest.maxPlayers() < manifest.minPlayers()) {
            throw new IllegalStateException("Le monde " + shortName + " a un maxPlayers (" + manifest.maxPlayers()
                    + ") inférieur à son minPlayers (" + manifest.minPlayers() + ")");
        }
        return new WorldTemplateSummary(manifest.id(), shortName, manifest.name(), manifest.description(),
                manifest.minPlayers(), manifest.maxPlayers());
    }

    private WorldTemplate loadFullTemplate(UUID id) {
        WorldTemplateSummary summary = summariesById.get(id);
        if (summary == null) {
            throw new IllegalStateException("WorldTemplate " + id + " absent de summariesById");
        }
        return loadWorldTemplate(summary, itemTemplateCatalog.templatesById());
    }

    private WorldTemplate loadWorldTemplate(WorldTemplateSummary summary, Map<UUID, ItemTemplate> itemTemplatesById) {
        String shortName = summary.shortName();
        List<ParsedMap> parsedMaps = readMapFiles();
        Map<UUID, MapTemplate> mapTemplates = buildMapTemplates(shortName, parsedMaps);

        List<NpcDefinition> npcDefinitionList = readJsonList("npcs.json", new TypeReference<List<NpcDefinition>>() {
        });
        Map<UUID, NpcDefinition> npcDefinitionsById = new LinkedHashMap<>();
        for (NpcDefinition definition : npcDefinitionList) {
            if (npcDefinitionsById.putIfAbsent(definition.id(), definition) != null) {
                throw new IllegalStateException("NPC " + definition.id() + " dupliqué dans data/npcs.json");
            }
        }
        Map<UUID, NpcTemplate> npcTemplates = buildNpcTemplates(shortName, npcDefinitionsById, mapTemplates,
                itemTemplatesById);

        WorldTemplate template = new WorldTemplate(summary.id(), shortName, summary.name(), summary.description(),
                summary.minPlayers(), summary.maxPlayers(), mapTemplates, npcTemplates);

        log.info("world.template_loaded shortName={} id={} maps={} npcs={}", shortName, template.getId(),
                mapTemplates.size(), npcTemplates.size());
        return template;
    }

    private List<ParsedMap> readMapFiles() {
        String pattern = "classpath*:" + DATA_DIR + "maps/*.tmx";
        Resource[] files;
        try {
            files = resourcePatternResolver.getResources(pattern);
        } catch (IOException e) {
            throw new IllegalStateException("Impossible d'énumérer " + pattern, e);
        }
        if (files.length == 0) {
            throw new IllegalStateException("Aucune map Tiled sous " + pattern);
        }
        List<ParsedMap> maps = new ArrayList<>();
        for (Resource file : files) {
            try (InputStream in = file.getInputStream()) {
                maps.add(TiledMapLoader.parse(TiledMapLoader.readMap(in), this::openMapTilesetResource));
            } catch (IOException e) {
                throw new IllegalStateException("Impossible de charger la map Tiled " + file, e);
            }
        }
        return maps;
    }

    private InputStream openMapTilesetResource(String relativeSource) {
        try {
            return worldFile("maps/" + relativeSource).getInputStream();
        } catch (IOException e) {
            throw new IllegalStateException("Impossible de charger le tileset " + relativeSource, e);
        }
    }

    Map<UUID, MapTemplate> buildMapTemplates(String shortName, List<ParsedMap> parsedMaps) {
        long startingMapCount = parsedMaps.stream().filter(ParsedMap::isStartingMap).count();
        if (startingMapCount != 1) {
            throw new IllegalStateException("Le monde " + shortName + " doit avoir exactement une map isStartingMap"
                    + " (trouvé " + startingMapCount + ")");
        }

        Map<UUID, MapTemplate> templates = new LinkedHashMap<>();
        for (ParsedMap map : parsedMaps) {
            MapTemplate template = new MapTemplate(map.id(), map.name(), map.description(), map.isStartingMap(),
                    map.terrain(), map.spawnPosition(), map.monsterSpawns(), map.monsterSpawnGroups(), map.npcSpawns());
            if (!template.isWalkable(map.spawnPosition())) {
                throw new IllegalStateException("Map " + map.id() + " du monde " + shortName + " a une position de "
                        + "spawn " + map.spawnPosition() + " non praticable de sa carte");
            }
            if (templates.putIfAbsent(template.getId(), template) != null) {
                throw new IllegalStateException("Map " + map.id() + " dupliquée dans le monde " + shortName);
            }
        }

        for (ParsedMap map : parsedMaps) {
            MapTemplate source = templates.get(map.id());
            List<MapTemplatePortal> portals = map.portals().stream()
                    .map(portal -> resolvePortal(shortName, map, source, portal, templates)).toList();
            checkNoOverlappingPortals(shortName, map, portals);
            source.setPortals(portals);
            source.setPeaceZones(
                    map.peaceZones().stream().map(zone -> new PeaceZone(zone.name(), zone.polygon())).toList());
        }

        return Map.copyOf(templates);
    }

    private MapTemplatePortal resolvePortal(String shortName, ParsedMap map, MapTemplate source, PortalDraft portal,
            Map<UUID, MapTemplate> templates) {
        MapTemplate target = templates.get(portal.targetMapId());
        if (target == null) {
            throw new IllegalStateException("Map " + map.id() + " du monde " + shortName + " a un portail '"
                    + portal.direction() + "' vers " + portal.targetMapId() + ", absente de ce monde");
        }

        if (!source.isWalkable(portal.position())) {
            throw new IllegalStateException("Map " + map.id() + " du monde " + shortName + " a un portail en "
                    + portal.position() + " non praticable de sa carte");
        }

        if (!target.isWalkable(portal.targetPosition())) {
            throw new IllegalStateException("Map " + map.id() + " du monde " + shortName + " a un portail vers "
                    + portal.targetPosition() + " non praticable de la carte de la map cible " + portal.targetMapId());
        }

        return new MapTemplatePortal(portal.position(), portal.direction(), target.getId(), portal.targetPosition(),
                portal.triggerRadius());
    }

    private void checkNoOverlappingPortals(String shortName, ParsedMap map, List<MapTemplatePortal> portals) {
        for (int i = 0; i < portals.size(); i++) {
            for (int j = i + 1; j < portals.size(); j++) {
                MapTemplatePortal a = portals.get(i);
                MapTemplatePortal b = portals.get(j);
                if (a.position().distanceTo(b.position()) < a.triggerRadius() + b.triggerRadius()) {
                    throw new IllegalStateException("Map " + map.id() + " du monde " + shortName
                            + " a des portails qui se chevauchent en " + a.position() + " et " + b.position());
                }
            }
        }
    }

    Map<UUID, NpcTemplate> buildNpcTemplates(String shortName, Map<UUID, NpcDefinition> definitionsById,
            Map<UUID, MapTemplate> mapTemplates, Map<UUID, ItemTemplate> itemTemplatesById) {
        Map<UUID, NpcTemplate> templates = new LinkedHashMap<>();
        for (MapTemplate map : mapTemplates.values()) {
            for (NpcSpawn spawn : map.getNpcSpawns()) {
                NpcDefinition definition = definitionsById.get(spawn.npcId());
                if (definition == null) {
                    throw new IllegalStateException("Spawn " + spawn.id() + " de la map " + map.getId() + " du monde "
                            + shortName + " référence le NPC " + spawn.npcId() + ", absent de data/npcs.json");
                }

                AbstractNpc.NpcDialogue dialogue = toDialogue(definition);
                NpcSellerInstance.NpcShop shop = toShop(shortName, definition, itemTemplatesById);

                NpcTemplate template = new NpcTemplate(definition.id(), definition.name(), map.getId(),
                        spawn.position(), definition.description(), dialogue, shop, definition.level(), Set.of(),
                        Set.of(), List.of());
                if (templates.putIfAbsent(template.id(), template) != null) {
                    throw new IllegalStateException("NPC " + definition.id() + " dupliqué dans le monde " + shortName);
                }
            }
        }
        return Map.copyOf(templates);
    }

    private AbstractNpc.NpcDialogue toDialogue(NpcDefinition definition) {
        DialogueDefinition dialogueDef = definition.dialogue();
        if (dialogueDef == null) {
            return null;
        }

        List<AbstractNpc.NpcDialogueOption> options = dialogueDef.options().stream()
                .map(o -> new AbstractNpc.NpcDialogueOption(o.label(), o.type(), o.response())).toList();
        return new AbstractNpc.NpcDialogue(dialogueDef.greeting(), options);
    }

    private NpcSellerInstance.NpcShop toShop(String shortName, NpcDefinition definition,
            Map<UUID, ItemTemplate> itemTemplatesById) {
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

        List<NpcSellerInstance.NpcShopEntry> entries = new ArrayList<>();
        for (ShopEntryDefinition entry : shopDef.items()) {
            ItemTemplate itemTemplate = itemTemplatesById.get(entry.itemTemplateId());
            if (itemTemplate == null) {
                throw new IllegalStateException("NPC " + definition.id() + " du monde " + shortName + " vend l'item "
                        + entry.itemTemplateId() + ", absent de data/items.json");
            }
            if (entry.price() <= 0) {
                throw new IllegalStateException("NPC " + definition.id() + " du monde " + shortName + " vend l'item "
                        + entry.itemTemplateId() + " à un prix invalide (" + entry.price() + ")");
            }
            entries.add(new NpcSellerInstance.NpcShopEntry(itemTemplate, entry.price()));
        }
        return new NpcSellerInstance.NpcShop(entries);
    }

    private <T> T readJson(String fileName, Class<T> type) {
        try (InputStream in = worldFile(fileName).getInputStream()) {
            return objectMapper.readValue(in, type);
        } catch (IOException | JacksonException e) {
            throw new IllegalStateException("Impossible de charger " + DATA_DIR + fileName, e);
        }
    }

    private <T> T readJsonList(String fileName, TypeReference<T> typeReference) {
        try (InputStream in = worldFile(fileName).getInputStream()) {
            return objectMapper.readValue(in, typeReference);
        } catch (IOException | JacksonException e) {
            throw new IllegalStateException("Impossible de charger " + DATA_DIR + fileName, e);
        }
    }

    private Resource worldFile(String fileName) {
        return resourcePatternResolver.getResource("classpath:" + DATA_DIR + fileName);
    }

    public WorldTemplateSummary theOnlyTemplate() {
        return summariesById.values().iterator().next();
    }

    public Optional<WorldTemplate> findById(UUID id) {
        if (!summariesById.containsKey(id)) {
            return Optional.empty();
        }
        return Optional.of(loadedTemplatesById.computeIfAbsent(id, this::loadFullTemplate));
    }

    record WorldManifestDefinition(UUID id, String name, String description, int minPlayers, int maxPlayers) {
    }

    record NpcDefinition(UUID id, String name, String description, DialogueDefinition dialogue, int level) {
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
