package app.game.catalog;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import app.domain.ConsumableEffect;
import app.domain.ActiveSkill;
import app.domain.EffectCategory;
import app.domain.SkillElement;
import app.domain.StatModifier;
import app.domain.StatOperator;
import app.domain.actor.ModifiedStat;
import app.domain.item.ArmorCategory;
import app.domain.item.ConsumableItem;
import app.domain.item.EquipmentItem;
import app.domain.item.ItemExpectation;
import app.domain.item.ItemGrade;
import app.domain.item.ItemTemplate;
import app.domain.item.ItemType;
import tools.jackson.core.JacksonException;
import tools.jackson.dataformat.xml.XmlMapper;
import tools.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import tools.jackson.dataformat.xml.annotation.JacksonXmlProperty;

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
            ItemExpectation expectation = toItemExpectation(definition.expect());

            ItemTemplate template = new EquipmentItem(definition.id(), definition.name(), definition.description(),
                    definition.type(), definition.weight(), definition.armorCategory(), definition.pAtk(),
                    definition.mAtk(), definition.pDef(), definition.mDef(), definition.accuracyBonus(),
                    definition.evasionBonus(), definition.critBonus(), definition.atkSpd(), definition.price(),
                    grantedSkills, elementalResistances, grade, definition.setId(), expectation);
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

    private record ConsumableDefinition(@JacksonXmlProperty(isAttribute = true) UUID id, String name,
            String description, ItemType type, int weight, int price, ItemGrade grade,
            ConsumableEffect consumableEffect, int effectAmount) {
    }

    private record EquipmentDefinition(@JacksonXmlProperty(isAttribute = true) UUID id, String name, String description,
            ItemType type, int weight, ArmorCategory armorCategory, int pAtk, int mAtk, int pDef, int mDef,
            int accuracyBonus, int evasionBonus, int critBonus, int atkSpd, int price,
            @JacksonXmlElementWrapper(useWrapping = false) List<UUID> grantedSkillIds,
            Map<SkillElement, Integer> elementalResistances, ItemGrade grade, String setId, ExpectXml expect) {
    }

    private record OtherDefinition(@JacksonXmlProperty(isAttribute = true) UUID id, String name, String description,
            ItemType type, int weight, int price, ItemGrade grade) {
    }

    private static ItemExpectation toItemExpectation(ExpectXml xml) {
        if (xml == null) {
            return null;
        }
        List<ItemExpectation.SkillRequirement> conditions = xml.conditions().skill().stream()
                .map(c -> new ItemExpectation.SkillRequirement(c.id(), c.level())).toList();
        List<ItemExpectation.ExpectationEffect> actions = xml.actions().effect().stream()
                .map(e -> new ItemExpectation.ExpectationEffect(e.name(), parseDuration(e.time()), e.type(),
                        e.apply().stream().map(a -> new StatModifier(a.stat(), a.value(), a.op())).toList()))
                .toList();
        return new ItemExpectation(conditions, actions);
    }

    private static Duration parseDuration(String time) {
        return "unlimited".equalsIgnoreCase(time) ? Duration.ofDays(3650) : Duration.ofSeconds(Long.parseLong(time));
    }

    // <expect> décrit les prérequis (compétences+level) d'un EquipmentItem et le
    // debuff à appliquer s'ils ne sont pas remplis (cf. ItemExpectation,
    // InventorySystem.recomputeGradePenalty). Chaque sous-liste passe par un objet
    // imbriqué unique (conditions/actions) plutôt qu'une liste wrappée : cf. le
    // commentaire sur EffectsXml dans SkillCatalog pour la raison (bug de
    // tools.jackson.dataformat.xml avec les records).
    private record ExpectXml(ConditionsXml conditions, ActionsXml actions) {
    }

    private record ConditionsXml(
            @JacksonXmlProperty(localName = "skill") @JacksonXmlElementWrapper(useWrapping = false) List<SkillRequirementXml> skill) {
    }

    private record SkillRequirementXml(@JacksonXmlProperty(isAttribute = true) UUID id,
            @JacksonXmlProperty(isAttribute = true) int level) {
    }

    private record ActionsXml(
            @JacksonXmlProperty(localName = "effect") @JacksonXmlElementWrapper(useWrapping = false) List<ExpectationEffectXml> effect) {
    }

    private record ExpectationEffectXml(@JacksonXmlProperty(isAttribute = true) String name,
            @JacksonXmlProperty(isAttribute = true) String time,
            @JacksonXmlProperty(isAttribute = true) EffectCategory type,
            @JacksonXmlProperty(localName = "apply") @JacksonXmlElementWrapper(useWrapping = false) List<StatModifierXml> apply) {
    }

    private record StatModifierXml(@JacksonXmlProperty(isAttribute = true) ModifiedStat stat,
            @JacksonXmlProperty(isAttribute = true) int value,
            @JacksonXmlProperty(isAttribute = true) StatOperator op) {
    }
}
