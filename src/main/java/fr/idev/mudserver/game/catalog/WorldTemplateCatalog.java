package fr.idev.mudserver.game.catalog;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
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

import fr.idev.mudserver.domain.map.HexCoordinate;
import fr.idev.mudserver.domain.item.ItemTemplate;
import fr.idev.mudserver.domain.world.ZoneTemplate;
import fr.idev.mudserver.domain.world.ZoneTemplatePortal;
import fr.idev.mudserver.domain.world.WorldTemplate;
import fr.idev.mudserver.domain.world.WorldTemplateSummary;
import fr.idev.mudserver.domain.actor.AbstractNpc;
import fr.idev.mudserver.domain.actor.AbstractNpc.NpcDialogueOptionType;
import fr.idev.mudserver.domain.actor.instance.NpcSellerInstance;
import fr.idev.mudserver.domain.actor.template.NpcTemplate;
import fr.idev.mudserver.game.catalog.tiled.TiledMap;
import fr.idev.mudserver.game.catalog.tiled.TiledZoneLoader;
import fr.idev.mudserver.game.catalog.tiled.TiledZoneLoader.ParsedZone;
import fr.idev.mudserver.game.catalog.tiled.TiledZoneLoader.PortalDraft;
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
        List<ParsedZone> parsedZones = readZoneFiles();
        Map<UUID, ZoneTemplate> zoneTemplates = buildZoneTemplates(shortName, parsedZones);

        List<NpcDefinition> npcDefinitions = readJsonList("npcs.json", new TypeReference<List<NpcDefinition>>() {
        });
        Map<UUID, NpcTemplate> npcTemplates = buildNpcTemplates(shortName, npcDefinitions, zoneTemplates,
                itemTemplatesById);

        WorldTemplate template = new WorldTemplate(summary.id(), shortName, summary.name(), summary.description(),
                summary.minPlayers(), summary.maxPlayers(), zoneTemplates, npcTemplates);

        log.info("world.template_loaded shortName={} id={} zones={} npcs={}", shortName, template.getId(),
                zoneTemplates.size(), npcTemplates.size());
        return template;
    }

    private List<ParsedZone> readZoneFiles() {
        String pattern = "classpath*:" + DATA_DIR + "zones/*.json";
        Resource[] files;
        try {
            files = resourcePatternResolver.getResources(pattern);
        } catch (IOException e) {
            throw new IllegalStateException("Impossible d'énumérer " + pattern, e);
        }
        if (files.length == 0) {
            throw new IllegalStateException("Aucune zone Tiled sous " + pattern);
        }
        List<ParsedZone> zones = new ArrayList<>();
        for (Resource file : files) {
            try (InputStream in = file.getInputStream()) {
                zones.add(TiledZoneLoader.parse(TiledZoneLoader.readMap(in)));
            } catch (IOException e) {
                throw new IllegalStateException("Impossible de charger la zone Tiled " + file, e);
            }
        }
        return zones;
    }

    Map<UUID, ZoneTemplate> buildZoneTemplates(String shortName, List<ParsedZone> parsedZones) {
        long startingZoneCount = parsedZones.stream().filter(ParsedZone::isStartingZone).count();
        if (startingZoneCount != 1) {
            throw new IllegalStateException("Le monde " + shortName + " doit avoir exactement une zone isStartingZone"
                    + " (trouvé " + startingZoneCount + ")");
        }

        Map<UUID, ZoneTemplate> templates = new LinkedHashMap<>();
        for (ParsedZone zone : parsedZones) {
            ZoneTemplate template = new ZoneTemplate(zone.id(), zone.name(), zone.description(), zone.isStartingZone(),
                    zone.terrain(), zone.spawnCell(), zone.monsterSpawns());
            if (!template.isWalkable(zone.spawnCell())) {
                throw new IllegalStateException("Zone " + zone.id() + " du monde " + shortName + " a une case de spawn "
                        + zone.spawnCell() + " absente ou non praticable de sa carte");
            }
            if (templates.putIfAbsent(template.getId(), template) != null) {
                throw new IllegalStateException("Zone " + zone.id() + " dupliquée dans le monde " + shortName);
            }
        }

        for (ParsedZone zone : parsedZones) {
            ZoneTemplate source = templates.get(zone.id());
            List<ZoneTemplatePortal> portals = zone.portals().stream()
                    .map(portal -> resolvePortal(shortName, zone, source, portal, templates)).toList();
            checkNoDuplicatePortalCell(shortName, zone, portals);
            source.setPortals(portals);
        }

        return Map.copyOf(templates);
    }

    private ZoneTemplatePortal resolvePortal(String shortName, ParsedZone zone, ZoneTemplate source, PortalDraft portal,
            Map<UUID, ZoneTemplate> templates) {
        ZoneTemplate target = templates.get(portal.targetZoneId());
        if (target == null) {
            throw new IllegalStateException("Zone " + zone.id() + " du monde " + shortName + " a un portail '"
                    + portal.direction() + "' vers " + portal.targetZoneId() + ", absente de ce monde");
        }

        if (!source.isWalkable(portal.cell())) {
            throw new IllegalStateException("Zone " + zone.id() + " du monde " + shortName + " a un portail en "
                    + portal.cell() + " absente ou non praticable de sa carte");
        }

        if (!target.isWalkable(portal.targetCell())) {
            throw new IllegalStateException(
                    "Zone " + zone.id() + " du monde " + shortName + " a un portail vers " + portal.targetCell()
                            + " absente ou non praticable de la carte de la zone cible " + portal.targetZoneId());
        }

        return new ZoneTemplatePortal(portal.cell(), portal.direction(), target.getId(), portal.targetCell());
    }

    private void checkNoDuplicatePortalCell(String shortName, ParsedZone zone, List<ZoneTemplatePortal> portals) {
        long distinctCells = portals.stream().map(ZoneTemplatePortal::cell).distinct().count();
        if (distinctCells != portals.size()) {
            throw new IllegalStateException(
                    "Zone " + zone.id() + " du monde " + shortName + " a plusieurs portails sur la même case");
        }
    }

    Map<UUID, NpcTemplate> buildNpcTemplates(String shortName, List<NpcDefinition> definitions,
            Map<UUID, ZoneTemplate> zoneTemplates, Map<UUID, ItemTemplate> itemTemplatesById) {
        Map<UUID, NpcTemplate> templates = new LinkedHashMap<>();
        for (NpcDefinition definition : definitions) {
            ZoneTemplate zone = zoneTemplates.get(definition.zoneId());
            if (zone == null) {
                throw new IllegalStateException("NPC " + definition.id() + " du monde " + shortName
                        + " référence la zone " + definition.zoneId() + ", absente de ce monde");
            }

            AbstractNpc.NpcDialogue dialogue = toDialogue(definition);
            NpcSellerInstance.NpcShop shop = toShop(shortName, definition, itemTemplatesById);

            NpcTemplate template = new NpcTemplate(definition.id(), definition.name(), definition.zoneId(),
                    new HexCoordinate(definition.cell().q(), definition.cell().r()), definition.description(), dialogue,
                    shop, definition.level());
            if (templates.putIfAbsent(template.id(), template) != null) {
                throw new IllegalStateException("NPC " + definition.id() + " dupliqué dans le monde " + shortName);
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

    record CellDefinition(int q, int r) {
    }

    record NpcDefinition(UUID id, String name, UUID zoneId, CellDefinition cell, String description,
            DialogueDefinition dialogue, int level) {
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
