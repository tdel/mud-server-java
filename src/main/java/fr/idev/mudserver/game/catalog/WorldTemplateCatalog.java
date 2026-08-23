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
import fr.idev.mudserver.domain.MonsterSpawn;
import fr.idev.mudserver.domain.world.ZoneTemplate;
import fr.idev.mudserver.domain.world.ZoneTemplatePortal;
import fr.idev.mudserver.domain.world.WorldTemplate;
import fr.idev.mudserver.domain.world.WorldTemplateSummary;
import fr.idev.mudserver.domain.actor.AbstractNpc;
import fr.idev.mudserver.domain.actor.AbstractNpc.NpcDialogueOptionType;
import fr.idev.mudserver.domain.actor.instance.NpcSellerInstance;
import fr.idev.mudserver.domain.actor.template.NpcTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
public class WorldTemplateCatalog {

    private static final Logger log = LoggerFactory.getLogger(WorldTemplateCatalog.class);

    private static final String WORLDS_MANIFEST_PATTERN = "classpath*:data/worlds/*/world.json";
    private static final String WORLDS_DIR_MARKER = "data/worlds/";

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
            manifests = resourcePatternResolver.getResources(WORLDS_MANIFEST_PATTERN);
        } catch (IOException e) {
            throw new IllegalStateException("Impossible d'énumérer " + WORLDS_MANIFEST_PATTERN, e);
        }
        if (manifests.length != 1) {
            throw new IllegalStateException("Un seul monde est supporté (retour au monde unique) : " + manifests.length
                    + " trouvé(s) sous " + WORLDS_MANIFEST_PATTERN);
        }

        Map<UUID, WorldTemplateSummary> loaded = new LinkedHashMap<>();
        for (Resource manifest : manifests) {
            String shortName = shortNameOf(manifest);
            WorldTemplateSummary summary = loadSummary(shortName);
            if (loaded.putIfAbsent(summary.id(), summary) != null) {
                throw new IllegalStateException(
                        "Le monde " + shortName + " a un id " + summary.id() + " déjà utilisé par un autre monde");
            }
        }

        summariesById.clear();
        summariesById.putAll(loaded);
        log.info("world.templates_loaded count={}", summariesById.size());
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

    private WorldTemplateSummary loadSummary(String shortName) {
        WorldManifestDefinition manifest = readJson(shortName, "world.json", WorldManifestDefinition.class);
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
        List<ZoneDefinition> zoneDefinitions = readJsonList(shortName, "zones.json",
                new TypeReference<List<ZoneDefinition>>() {
                });
        Map<UUID, ZoneTemplate> zoneTemplates = buildZoneTemplates(shortName, zoneDefinitions);

        List<NpcDefinition> npcDefinitions = readJsonList(shortName, "npcs.json",
                new TypeReference<List<NpcDefinition>>() {
                });
        Map<UUID, NpcTemplate> npcTemplates = buildNpcTemplates(shortName, npcDefinitions, zoneTemplates,
                itemTemplatesById);

        WorldTemplate template = new WorldTemplate(summary.id(), shortName, summary.name(), summary.description(),
                summary.minPlayers(), summary.maxPlayers(), zoneTemplates, npcTemplates);

        log.info("world.template_loaded shortName={} id={} zones={} npcs={}", shortName, template.getId(),
                zoneTemplates.size(), npcTemplates.size());
        return template;
    }

    Map<UUID, ZoneTemplate> buildZoneTemplates(String shortName, List<ZoneDefinition> definitions) {
        long startingZoneCount = definitions.stream().filter(d -> Boolean.TRUE.equals(d.isStartingZone())).count();
        if (startingZoneCount != 1) {
            throw new IllegalStateException("Le monde " + shortName + " doit avoir exactement une zone isStartingZone"
                    + " (trouvé " + startingZoneCount + ")");
        }

        Map<UUID, ZoneTemplate> templates = new LinkedHashMap<>();
        for (ZoneDefinition definition : definitions) {
            HexCoordinate spawnCell = new HexCoordinate(definition.spawnCell().q(), definition.spawnCell().r());
            if (definition.width() <= 0 || definition.height() <= 0) {
                throw new IllegalStateException("Zone " + definition.id() + " du monde " + shortName
                        + " a une grille invalide (" + definition.width() + "x" + definition.height() + ")");
            }
            ZoneTemplate template = new ZoneTemplate(definition.id(), definition.name(), definition.description(),
                    definition.isStartingZone(), definition.width(), definition.height(), spawnCell,
                    definition.monsterSpawns().stream().map(spawn -> new MonsterSpawn(spawn.id(), spawn.templateId(),
                            new HexCoordinate(spawn.cell().q(), spawn.cell().r()))).toList());
            if (!template.isInBounds(spawnCell)) {
                throw new IllegalStateException("Zone " + definition.id() + " du monde " + shortName
                        + " a une case de spawn " + spawnCell + " hors des bornes de sa grille (" + definition.width()
                        + "x" + definition.height() + ")");
            }
            if (templates.putIfAbsent(template.getId(), template) != null) {
                throw new IllegalStateException("Zone " + definition.id() + " dupliquée dans le monde " + shortName);
            }
        }

        for (ZoneDefinition definition : definitions) {
            ZoneTemplate source = templates.get(definition.id());
            List<ZoneTemplatePortal> portals = definition.portals().stream()
                    .map(portal -> resolvePortal(shortName, definition, source, portal, templates)).toList();
            checkNoDuplicatePortalCell(shortName, definition, portals);
            source.setPortals(portals);
        }

        return Map.copyOf(templates);
    }

    private ZoneTemplatePortal resolvePortal(String shortName, ZoneDefinition definition, ZoneTemplate source,
            PortalDefinition portal, Map<UUID, ZoneTemplate> templates) {
        ZoneTemplate target = templates.get(portal.targetZoneId());
        if (target == null) {
            throw new IllegalStateException("Zone " + definition.id() + " du monde " + shortName + " a un portail '"
                    + portal.direction() + "' vers " + portal.targetZoneId() + ", absente de ce monde");
        }

        HexCoordinate cell = new HexCoordinate(portal.cell().q(), portal.cell().r());
        if (!source.isBorderCell(cell)) {
            throw new IllegalStateException("Zone " + definition.id() + " du monde " + shortName + " a un portail en "
                    + cell + " hors des bords de sa grille (" + source.getWidth() + "x" + source.getHeight() + ")");
        }

        HexCoordinate targetCell = new HexCoordinate(portal.targetCell().q(), portal.targetCell().r());
        if (!target.isInBounds(targetCell)) {
            throw new IllegalStateException("Zone " + definition.id() + " du monde " + shortName + " a un portail vers "
                    + targetCell + " hors des bornes de la grille de la zone cible " + portal.targetZoneId() + " ("
                    + target.getWidth() + "x" + target.getHeight() + ")");
        }

        return new ZoneTemplatePortal(cell, portal.direction(), target.getId(), targetCell);
    }

    private void checkNoDuplicatePortalCell(String shortName, ZoneDefinition definition,
            List<ZoneTemplatePortal> portals) {
        long distinctCells = portals.stream().map(ZoneTemplatePortal::cell).distinct().count();
        if (distinctCells != portals.size()) {
            throw new IllegalStateException(
                    "Zone " + definition.id() + " du monde " + shortName + " a plusieurs portails sur la même case");
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

    record ZoneDefinition(UUID id, String name, String description, Boolean isStartingZone, int width, int height,
            CellDefinition spawnCell, List<PortalDefinition> portals, List<MonsterSpawnDefinition> monsterSpawns) {
    }

    record CellDefinition(int q, int r) {
    }

    record MonsterSpawnDefinition(UUID id, UUID templateId, CellDefinition cell) {
    }

    record PortalDefinition(CellDefinition cell, String direction, UUID targetZoneId, CellDefinition targetCell) {
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
