package fr.idev.mudserver.game;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import fr.idev.mudserver.domain.ArmorCategory;
import fr.idev.mudserver.domain.ConsumableEffect;
import fr.idev.mudserver.domain.ConsumableItem;
import fr.idev.mudserver.domain.FoodItem;
import fr.idev.mudserver.domain.ItemTemplate;
import fr.idev.mudserver.domain.ItemType;
import fr.idev.mudserver.domain.Rarity;
import fr.idev.mudserver.domain.WeaponCategory;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
public class ItemTemplateService {

    private static final Logger log = LoggerFactory.getLogger(ItemTemplateService.class);

    private static final String ITEM_TEMPLATE_RESOURCE = "/data/items.json";

    private final Map<UUID, ItemTemplate> templates = new ConcurrentHashMap<>();

    private final ObjectMapper objectMapper;

    public ItemTemplateService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void warmItemTemplates() {
        try (InputStream in = getClass().getResourceAsStream(ITEM_TEMPLATE_RESOURCE)) {
            List<ItemTemplateDefinition> definitions = objectMapper.readValue(in,
                    new TypeReference<List<ItemTemplateDefinition>>() {
                    });
            for (ItemTemplateDefinition definition : definitions) {
                ItemTemplate template = toTemplate(definition);
                templates.put(template.getId(), template);
            }
            log.info("item.templates_loaded count={}", templates.size());
        } catch (IOException | JacksonException e) {
            throw new IllegalStateException("Impossible de charger " + ITEM_TEMPLATE_RESOURCE, e);
        }
    }

    private ItemTemplate toTemplate(ItemTemplateDefinition definition) {
        if (definition.consumableEffect() != null) {
            return new ConsumableItem(definition.id(), definition.name(), definition.description(), definition.type(),
                    definition.weight(), definition.armorCategory(), definition.baseAc(), definition.damageDice(),
                    definition.weaponCategory(), definition.price(), definition.rarity(), definition.bonus(),
                    definition.consumableEffect(), definition.effectDice());
        }
        if (definition.nutritionValue() != null) {
            return new FoodItem(definition.id(), definition.name(), definition.description(), definition.type(),
                    definition.weight(), definition.armorCategory(), definition.baseAc(), definition.damageDice(),
                    definition.weaponCategory(), definition.price(), definition.rarity(), definition.bonus(),
                    definition.nutritionValue());
        }
        return new ItemTemplate(definition.id(), definition.name(), definition.description(), definition.type(),
                definition.weight(), definition.armorCategory(), definition.baseAc(), definition.damageDice(),
                definition.weaponCategory(), definition.price(), definition.rarity(), definition.bonus());
    }

    public Set<UUID> templateIds() {
        return Set.copyOf(templates.keySet());
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

    private record ItemTemplateDefinition(UUID id, String name, String description, ItemType type, int weight,
            ArmorCategory armorCategory, int baseAc, String damageDice, WeaponCategory weaponCategory, int price,
            Rarity rarity, int bonus, ConsumableEffect consumableEffect, String effectDice, Integer nutritionValue) {
    }
}
