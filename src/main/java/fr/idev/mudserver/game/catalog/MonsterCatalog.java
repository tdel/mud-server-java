package fr.idev.mudserver.game.catalog;

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

import fr.idev.mudserver.domain.actor.Attribute;
import fr.idev.mudserver.domain.actor.instance.MonsterInstance;
import fr.idev.mudserver.domain.actor.template.MonsterTemplate;
import fr.idev.mudserver.domain.actor.template.MonsterTemplate.LootTableEntry;
import fr.idev.mudserver.domain.item.ItemTemplate;
import fr.idev.mudserver.domain.MonsterSpawn;
import fr.idev.mudserver.domain.world.RoomInstance;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
public class MonsterCatalog {

    private static final Logger log = LoggerFactory.getLogger(MonsterCatalog.class);

    private static final String MONSTERS_RESOURCE = "/data/monsters.json";

    private final Map<UUID, MonsterTemplate> templates = new ConcurrentHashMap<>();

    private final ObjectMapper objectMapper;
    private final ItemTemplateCatalog itemTemplateCatalog;

    public MonsterCatalog(ObjectMapper objectMapper, ItemTemplateCatalog itemTemplateCatalog) {
        this.objectMapper = objectMapper;
        this.itemTemplateCatalog = itemTemplateCatalog;
    }

    public void warmMonsterTemplates() {
        try (InputStream in = getClass().getResourceAsStream(MONSTERS_RESOURCE)) {
            List<MonsterTemplateDefinition> definitions = objectMapper.readValue(in,
                    new TypeReference<List<MonsterTemplateDefinition>>() {
                    });
            registerTemplates(definitions);
        } catch (IOException | JacksonException e) {
            throw new IllegalStateException("Impossible de charger " + MONSTERS_RESOURCE, e);
        }
    }

    public void placeMonsters(Collection<RoomInstance> rooms) {
        int placedCount = 0;
        for (RoomInstance room : rooms) {
            for (MonsterSpawn spawn : room.getMonsterSpawns()) {
                MonsterTemplate template = templates.get(spawn.templateId());
                if (template == null) {
                    throw new IllegalStateException("Spawn " + spawn.id() + " de la room " + room.getId()
                            + " référence le template " + spawn.templateId() + ", absent de " + MONSTERS_RESOURCE);
                }

                MonsterInstance monster = new MonsterInstance(spawn.id(), template, room);
                room.placeMonster(monster, spawn.cell());
                placedCount++;
            }
        }

        log.info("monster.instances_placed count={}", placedCount);
    }

    private void registerTemplates(List<MonsterTemplateDefinition> definitions) {
        for (MonsterTemplateDefinition definition : definitions) {
            List<LootTableEntry> lootTable = new ArrayList<>();
            for (LootTableEntryDefinition entryDef : definition.lootTable()) {
                if (entryDef.dropChance() < 0 || entryDef.dropChance() > 100) {
                    throw new IllegalStateException("Template " + definition.id() + " a une entrée de lootTable avec "
                            + "dropChance=" + entryDef.dropChance() + " hors de [0, 100] dans " + MONSTERS_RESOURCE);
                }
                ItemTemplate itemTemplate = this.itemTemplateCatalog.getById(entryDef.itemTemplateId());
                if (itemTemplate == null) {
                    throw new IllegalStateException("Template " + definition.id() + " référence l'item "
                            + entryDef.itemTemplateId() + " dans sa lootTable, absent de data/items.json");
                }
                lootTable.add(new LootTableEntry(itemTemplate, entryDef.dropChance()));
            }
            templates.put(definition.id(),
                    new MonsterTemplate(definition.id(), definition.name(), definition.description(),
                            definition.maxHealth(), definition.attributes(), definition.naturalArmorClass(),
                            definition.xpReward(), definition.naturalDamageDice(), definition.goldReward(), lootTable,
                            definition.aggroRadius(), definition.speed(), definition.level()));
        }
        log.info("monster.templates_loaded count={}", templates.size());
    }

    record LootTableEntryDefinition(UUID itemTemplateId, double dropChance) {
    }

    record MonsterTemplateDefinition(UUID id, String name, String description, int maxHealth,
            Map<Attribute, Integer> attributes, Integer naturalArmorClass, int xpReward, String naturalDamageDice,
            int goldReward, List<LootTableEntryDefinition> lootTable, int aggroRadius, int speed, int level) {
    }
}
