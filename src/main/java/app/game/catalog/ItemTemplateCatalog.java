package app.game.catalog;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import app.domain.ConsumableEffect;
import app.domain.ActiveSkill;
import app.domain.SkillElement;
import app.domain.item.ArmorCategory;
import app.domain.item.ConsumableItem;
import app.domain.item.EquipmentItem;
import app.domain.item.ItemGrade;
import app.domain.item.ItemTemplate;
import app.domain.item.ItemType;
import tools.jackson.core.JacksonException;
import tools.jackson.dataformat.xml.XmlMapper;
import tools.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;

@Service
public class ItemTemplateCatalog {

    private static final Logger log = LoggerFactory.getLogger(ItemTemplateCatalog.class);

    private static final String CONSUMABLES_RESOURCE = "/data/items/consumables.xml";
    private static final String ARMORS_RESOURCE = "/data/items/armors.xml";
    private static final String WEAPONS_RESOURCE = "/data/items/weapons.xml";
    private static final String JEWELLERY_RESOURCE = "/data/items/jewellery.xml";
    private static final String OTHERS_RESOURCE = "/data/items/others.xml";

    private final Map<UUID, ItemTemplate> templates = new ConcurrentHashMap<>();

    private final XmlMapper xmlMapper;
    private final SkillCatalog skillCatalog;

    public ItemTemplateCatalog(XmlMapper xmlMapper, SkillCatalog skillCatalog) {
        this.xmlMapper = xmlMapper;
        this.skillCatalog = skillCatalog;
    }

    public void warmItemTemplates() {
        loadConsumables();
        loadEquipment(ARMORS_RESOURCE);
        loadEquipment(WEAPONS_RESOURCE);
        loadEquipment(JEWELLERY_RESOURCE);
        loadOthers();
        log.info("item.templates_loaded count={}", templates.size());
    }

    private void loadConsumables() {
        for (ConsumableDefinition definition : readResource(CONSUMABLES_RESOURCE, ConsumableDefinition.class)) {
            ItemGrade grade = definition.grade() == null ? ItemGrade.NOGRADE : definition.grade();
            ItemTemplate template = new ConsumableItem(definition.id(), definition.name(), definition.description(),
                    definition.type(), definition.weight(), definition.price(), grade, definition.consumableEffect(),
                    definition.effectAmount());
            templates.put(template.getId(), template);
        }
    }

    private void loadEquipment(String resource) {
        for (EquipmentDefinition definition : readResource(resource, EquipmentDefinition.class)) {
            List<ActiveSkill> grantedSkills = definition.grantedSkillIds() == null
                    ? List.of()
                    : definition.grantedSkillIds().stream().map(skillCatalog::getById).toList();
            Map<SkillElement, Integer> elementalResistances = definition.elementalResistances() == null
                    ? Map.of()
                    : definition.elementalResistances();
            ItemGrade grade = definition.grade() == null ? ItemGrade.NOGRADE : definition.grade();

            ItemTemplate template = new EquipmentItem(definition.id(), definition.name(), definition.description(),
                    definition.type(), definition.weight(), definition.armorCategory(), definition.pAtk(),
                    definition.mAtk(), definition.pDef(), definition.mDef(), definition.accuracyBonus(),
                    definition.evasionBonus(), definition.critBonus(), definition.atkSpd(), definition.price(),
                    grantedSkills, elementalResistances, grade, definition.setId());
            templates.put(template.getId(), template);
        }
    }

    private void loadOthers() {
        for (OtherDefinition definition : readResource(OTHERS_RESOURCE, OtherDefinition.class)) {
            ItemGrade grade = definition.grade() == null ? ItemGrade.NOGRADE : definition.grade();
            ItemTemplate template = new ItemTemplate(definition.id(), definition.name(), definition.description(),
                    definition.type(), definition.weight(), definition.price(), grade);
            templates.put(template.getId(), template);
        }
    }

    private <T> List<T> readResource(String resource, Class<T> elementType) {
        try (InputStream in = getClass().getResourceAsStream(resource)) {
            return xmlMapper.readValue(in, xmlMapper.getTypeFactory().constructCollectionType(List.class, elementType));
        } catch (IOException | JacksonException e) {
            throw new IllegalStateException("Impossible de charger " + resource, e);
        }
    }

    public Map<UUID, ItemTemplate> templatesById() {
        return Map.copyOf(templates);
    }

    public ItemTemplate getById(UUID templateId) {
        ItemTemplate template = templates.get(templateId);
        if (template == null) {
            throw new IllegalStateException(
                    "ItemTemplate " + templateId + " absent du cache — warmItemTemplates() a-t-il été appelé ?");
        }
        return template;
    }

    private record ConsumableDefinition(UUID id, String name, String description, ItemType type, int weight, int price,
            ItemGrade grade, ConsumableEffect consumableEffect, int effectAmount) {
    }

    private record EquipmentDefinition(UUID id, String name, String description, ItemType type, int weight,
            ArmorCategory armorCategory, int pAtk, int mAtk, int pDef, int mDef, int accuracyBonus, int evasionBonus,
            int critBonus, int atkSpd, int price,
            @JacksonXmlElementWrapper(useWrapping = false) List<UUID> grantedSkillIds,
            Map<SkillElement, Integer> elementalResistances, ItemGrade grade, String setId) {
    }

    private record OtherDefinition(UUID id, String name, String description, ItemType type, int weight, int price,
            ItemGrade grade) {
    }
}
