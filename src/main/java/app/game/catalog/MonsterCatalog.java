package app.game.catalog;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import app.domain.SkillElement;
import app.domain.actor.Attribute;
import app.domain.actor.ModifiedStat;
import app.domain.actor.instance.MonsterInstance;
import app.domain.actor.template.MonsterTemplate;
import app.domain.item.ItemTemplate;
import app.domain.item.LootTableEntry;
import app.domain.MonsterSpawn;
import app.domain.MonsterSpawnGroup;
import app.domain.world.MapInstance;
import app.game.combat.CombatFormulas;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.dataformat.xml.XmlMapper;
import tools.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import tools.jackson.dataformat.xml.annotation.JacksonXmlProperty;

@Service
public class MonsterCatalog {

    private static final Logger log = LoggerFactory.getLogger(MonsterCatalog.class);

    private static final String MONSTERS_RESOURCE = "/data/monsters.xml";

    private final Map<UUID, MonsterTemplate> templates = new ConcurrentHashMap<>();

    private final XmlMapper xmlMapper;
    private final ItemTemplateCatalog itemTemplateCatalog;

    public MonsterCatalog(XmlMapper xmlMapper, ItemTemplateCatalog itemTemplateCatalog) {
        this.xmlMapper = xmlMapper;
        this.itemTemplateCatalog = itemTemplateCatalog;
    }

    public void warmMonsterTemplates() {
        try (InputStream in = getClass().getResourceAsStream(MONSTERS_RESOURCE)) {
            List<MonsterTemplateDefinition> definitions = xmlMapper.readValue(in,
                    new TypeReference<List<MonsterTemplateDefinition>>() {
                    });
            registerTemplates(definitions);
        } catch (IOException | JacksonException e) {
            throw new IllegalStateException("Impossible de charger " + MONSTERS_RESOURCE, e);
        }
    }

    public void placeMonsters(Collection<MapInstance> maps) {
        int placedCount = 0;
        for (MapInstance map : maps) {
            Map<String, MonsterSpawnGroup> groupsById = map.getMonsterSpawnGroups().stream()
                    .collect(Collectors.toMap(MonsterSpawnGroup::id, group -> group));
            Map<String, List<MonsterSpawn>> spawnsByGroup = map.getMonsterSpawns().stream()
                    .collect(Collectors.groupingBy(MonsterSpawn::groupId));

            for (Map.Entry<String, List<MonsterSpawn>> entry : spawnsByGroup.entrySet()) {
                MonsterSpawnGroup group = groupsById.get(entry.getKey());
                List<MonsterSpawn> spawns = entry.getValue();
                int toPlace = Math.min(group.maxMonsters(), spawns.size());
                for (MonsterSpawn spawn : spawns.subList(0, toPlace)) {
                    spawnMonster(spawn, map);
                    placedCount++;
                }
            }
        }

        log.info("monster.instances_placed count={}", placedCount);
    }

    public MonsterInstance spawnMonster(MonsterSpawn spawn, MapInstance map) {
        MonsterTemplate template = templates.get(spawn.templateId());
        if (template == null) {
            throw new IllegalStateException("Spawn " + spawn.id() + " de la map " + map.getId()
                    + " référence le template " + spawn.templateId() + ", absent de " + MONSTERS_RESOURCE);
        }

        Map<ModifiedStat, Integer> baseStats = CombatFormulas.baseStats(template.getPAtk(), template.getMAtk(),
                template.getPDef(), template.getMDef(), template.getAccuracyBonus(), template.getEvasionBonus(),
                template.getCritBonus(), 0, template.getAtkSpd(), template.getAttributes(), template.getLevel());
        baseStats.put(ModifiedStat.SPEED, template.getSpeed());

        MonsterInstance monster = new MonsterInstance(spawn.id(), template.getName(), template.getAttributes(),
                template.getMaxHealth(), baseStats, spawn.position(), template.getKnownSkills(),
                template.getKnownPassiveSkills(), template.getActiveEffects(), template.getLevel(),
                template.getAggroRadius(), template.getElementalResistances(), template.getXpReward(),
                template.getGoldReward(), template.getLootTable());
        monster.getMotionSystem().setCurrentMap(map);
        map.placeMonster(monster, spawn.position());
        return monster;
    }

    private void registerTemplates(List<MonsterTemplateDefinition> definitions) {
        for (MonsterTemplateDefinition definition : definitions) {
            List<LootTableEntry> lootTable = new ArrayList<>();
            List<ItemDropDefinition> itemDrops = definition.loot().item() == null
                    ? List.of()
                    : definition.loot().item();
            for (ItemDropDefinition drop : itemDrops) {
                if (drop.chance() < 0 || drop.chance() > 100) {
                    throw new IllegalStateException("Template " + definition.id() + " a une entrée de loot avec "
                            + "chance=" + drop.chance() + " hors de [0, 100] dans " + MONSTERS_RESOURCE);
                }
                ItemTemplate itemTemplate = this.itemTemplateCatalog.getById(drop.templateId());
                if (itemTemplate == null) {
                    throw new IllegalStateException("Template " + definition.id() + " référence l'item "
                            + drop.templateId() + " dans son loot, absent de data/items/*.xml");
                }
                lootTable.add(new LootTableEntry(itemTemplate, drop.chance()));
            }
            Map<SkillElement, Integer> elementalResistances = definition.elementalResistances() == null
                    ? Map.of()
                    : definition.elementalResistances();
            templates.put(definition.id(),
                    new MonsterTemplate(definition.id(), definition.name(), definition.maxHealth(),
                            definition.attributes(), definition.pAtk(), definition.mAtk(), definition.pDef(),
                            definition.mDef(), definition.accuracyBonus(), definition.evasionBonus(),
                            definition.critBonus(), definition.xpReward(), definition.loot().gold(), lootTable,
                            definition.aggroRadius(), definition.speed(), definition.atkSpd(), definition.level(),
                            elementalResistances, Set.of(), Set.of(), List.of()));
        }
        log.info("monster.templates_loaded count={}", templates.size());
    }

    record ItemDropDefinition(@JacksonXmlProperty(isAttribute = true) UUID templateId,
            @JacksonXmlProperty(isAttribute = true) double chance) {
    }

    record LootDefinition(int gold, @JacksonXmlElementWrapper(useWrapping = false) List<ItemDropDefinition> item) {
    }

    record MonsterTemplateDefinition(@JacksonXmlProperty(isAttribute = true) UUID id, String name, int maxHealth,
            Map<Attribute, Integer> attributes, int pAtk, int mAtk, int pDef, int mDef, int accuracyBonus,
            int evasionBonus, int critBonus, int xpReward, LootDefinition loot, int aggroRadius, int speed, int atkSpd,
            int level, Map<SkillElement, Integer> elementalResistances) {
    }
}
