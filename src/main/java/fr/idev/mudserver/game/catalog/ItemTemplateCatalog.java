package fr.idev.mudserver.game.catalog;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import fr.idev.mudserver.domain.item.ArmorCategory;
import fr.idev.mudserver.domain.ConsumableEffect;
import fr.idev.mudserver.domain.Spell;
import fr.idev.mudserver.domain.item.ConsumableItem;
import fr.idev.mudserver.domain.item.FoodItem;
import fr.idev.mudserver.domain.item.ItemTemplate;
import fr.idev.mudserver.domain.item.ItemType;
import fr.idev.mudserver.domain.item.Rarity;
import fr.idev.mudserver.domain.item.WeaponCategory;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
public class ItemTemplateCatalog {

    private static final Logger log = LoggerFactory.getLogger(ItemTemplateCatalog.class);

    private static final String ITEM_TEMPLATE_RESOURCE = "/data/items.json";

    private final Map<UUID, ItemTemplate> templates = new ConcurrentHashMap<>();

    private final ObjectMapper objectMapper;
    private final SpellCatalog spellCatalog;

    public ItemTemplateCatalog(ObjectMapper objectMapper, SpellCatalog spellCatalog) {
        this.objectMapper = objectMapper;
        this.spellCatalog = spellCatalog;
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
        List<Spell> grantedSpells = definition.grantedSpellIds() == null
                ? List.of()
                : definition.grantedSpellIds().stream().map(spellCatalog::getById).toList();

        if (definition.consumableEffect() != null) {
            return new ConsumableItem(definition.id(), definition.name(), definition.description(), definition.type(),
                    definition.weight(), definition.armorCategory(), definition.baseAc(), definition.damageDice(),
                    definition.weaponCategory(), definition.price(), definition.rarity(), definition.bonus(),
                    grantedSpells, definition.consumableEffect(), definition.effectDice());
        }
        if (definition.nutritionValue() != null) {
            return new FoodItem(definition.id(), definition.name(), definition.description(), definition.type(),
                    definition.weight(), definition.armorCategory(), definition.baseAc(), definition.damageDice(),
                    definition.weaponCategory(), definition.price(), definition.rarity(), definition.bonus(),
                    grantedSpells, definition.nutritionValue());
        }
        return new ItemTemplate(definition.id(), definition.name(), definition.description(), definition.type(),
                definition.weight(), definition.armorCategory(), definition.baseAc(), definition.damageDice(),
                definition.weaponCategory(), definition.price(), definition.rarity(), definition.bonus(),
                grantedSpells);
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
            Rarity rarity, int bonus, List<UUID> grantedSpellIds, ConsumableEffect consumableEffect, String effectDice,
            Integer nutritionValue) {
    }
}
