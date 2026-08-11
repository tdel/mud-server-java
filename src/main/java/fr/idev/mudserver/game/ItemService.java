package fr.idev.mudserver.game;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import fr.idev.mudserver.domain.ArmorCategory;
import fr.idev.mudserver.domain.ConsumableEffect;
import fr.idev.mudserver.domain.ConsumableItem;
import fr.idev.mudserver.domain.FoodItem;
import fr.idev.mudserver.domain.actor.GamePlayer;
import fr.idev.mudserver.domain.Item;
import fr.idev.mudserver.domain.ItemTemplate;
import fr.idev.mudserver.domain.ItemType;
import fr.idev.mudserver.domain.Rarity;
import fr.idev.mudserver.domain.RoomInstance;
import fr.idev.mudserver.domain.WeaponCategory;
import fr.idev.mudserver.domain.actor.event.CharacterLootedItem;
import fr.idev.mudserver.domain.actor.event.GamePlayerDroppedItem;
import fr.idev.mudserver.domain.actor.event.GamePlayerEquippedItem;
import fr.idev.mudserver.domain.actor.event.GamePlayerUnequippedItem;
import fr.idev.mudserver.domain.actor.event.GamePlayerUsedPotion;
import fr.idev.mudserver.domain.actor.event.ItemPickedUp;
import fr.idev.mudserver.domain.actor.event.ItemPurchased;
import fr.idev.mudserver.domain.actor.event.LongRestTaken;
import fr.idev.mudserver.game.dice.DiceRoller;
import fr.idev.mudserver.network.message.ingame.EquipmentLooted;
import fr.idev.mudserver.network.message.ingame.ItemBought;
import fr.idev.mudserver.persistence.ItemDao;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
public class ItemService {

    private static final Logger log = LoggerFactory.getLogger(ItemService.class);

    private static final String ITEM_TEMPLATE_RESOURCE = "/data/items.json";

    private final Map<UUID, ItemTemplate> templates = new ConcurrentHashMap<>();

    private final ItemDao itemDao;
    private final ObjectMapper objectMapper;

    public ItemService(ItemDao itemDao, ObjectMapper objectMapper) {
        this.itemDao = itemDao;
        this.objectMapper = objectMapper;
    }

    public void warmItemTemplates() {
        try (InputStream in = getClass().getResourceAsStream(ITEM_TEMPLATE_RESOURCE)) {
            List<ItemTemplateDefinition> definitions = objectMapper.readValue(in,
                    new TypeReference<List<ItemTemplateDefinition>>() {
                    });
            for (ItemTemplateDefinition definition : definitions) {
                registerTemplate(toTemplate(definition));
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

    void registerTemplate(ItemTemplate template) {
        templates.put(template.getId(), template);
    }

    public Set<UUID> templateIds() {
        return Set.copyOf(templates.keySet());
    }

    public Map<UUID, ItemSummary> templateSummariesById() {
        Map<UUID, ItemSummary> summaries = new ConcurrentHashMap<>();
        templates.forEach(
                (id, template) -> summaries.put(id, new ItemSummary(template.getName(), template.getRarity())));
        return summaries;
    }

    public record ItemSummary(String name, Rarity rarity) {
    }

    public List<Item> loadInventory(GamePlayer character) {
        List<Item> items = attachTemplates(itemDao.findByCharacterId(character.getId()));
        items.forEach(item -> item.attachCharacter(character));
        return items;
    }

    public void warmRoomItems(Collection<RoomInstance> rooms) {
        int totalItems = 0;
        for (RoomInstance room : rooms) {
            List<Item> items = attachTemplates(itemDao.findByRoomId(room.getId()));
            items.forEach(item -> item.attachRoom(room));
            room.setItems(items);
            totalItems += items.size();
        }
        log.info("item.room_items_loaded count={} rooms={}", totalItems, rooms.size());
    }

    Item attachTemplate(Item item) {
        ItemTemplate template = templates.get(item.getTemplateId());
        if (template == null) {
            throw new IllegalStateException("ItemTemplate " + item.getTemplateId()
                    + " absent du cache — warmItemTemplates() a-t-il été appelé ?");
        }
        item.attachTemplate(template);
        return item;
    }

    private List<Item> attachTemplates(List<Item> items) {
        items.forEach(this::attachTemplate);
        return items;
    }

    @EventListener
    void onItemPickedUp(ItemPickedUp event) {
        itemDao.assignToCharacter(event.item().getId(), event.character().getId());
        log.info("item.picked_up item={} template={} character={}", event.item().getId(), event.item().getTemplateId(),
                event.character().getName());
    }

    @EventListener
    void onGamePlayerDroppedItem(GamePlayerDroppedItem event) {
        itemDao.assignToRoom(event.item().getId(), event.room().getId());
        log.info("item.dropped item={} template={} room={}", event.item().getId(), event.item().getTemplateId(),
                event.room().getName());
    }

    @EventListener
    @Transactional
    void onGamePlayerEquippedItem(GamePlayerEquippedItem event) {
        for (Item previousOccupant : event.previousOccupants()) {
            itemDao.updateSlot(previousOccupant.getId(), null);
        }
        itemDao.updateSlot(event.item().getId(), event.slot());
        log.info("item.equipped item={} slot={} character={} previousOccupants={}", event.item().getName(),
                event.slot(), event.character().getName(), event.previousOccupants().size());
    }

    @EventListener
    void onGamePlayerUnequippedItem(GamePlayerUnequippedItem event) {
        itemDao.updateSlot(event.item().getId(), null);
        log.info("item.unequipped item={} character={}", event.item().getName(), event.character().getName());
    }

    @EventListener
    void onCharacterLootedItem(CharacterLootedItem event) {
        attachTemplate(event.item());
        itemDao.insert(event.item());
        event.character().send(new EquipmentLooted(event.item().getName(), event.item().getRarity()));
        log.info("item.looted item={} character={}", event.item().getName(), event.character().getName());
    }

    @EventListener
    void onItemPurchased(ItemPurchased event) {
        attachTemplate(event.item());
        itemDao.insert(event.item());
        event.character().send(new ItemBought(event.item().getName(), event.item().getRarity(), event.price()));
        log.info("item.purchased item={} character={} price={}", event.item().getName(), event.character().getName(),
                event.price());
    }

    @EventListener
    void onGamePlayerUsedPotion(GamePlayerUsedPotion event) {
        itemDao.delete(event.item().getId());
        log.info("item.consumed item={} character={} healedAmount={}", event.item().getId(),
                event.character().getName(), event.healedAmount());
    }

    @EventListener
    void onLongRestTaken(LongRestTaken event) {
        for (Item food : event.consumedFood()) {
            itemDao.delete(food.getId());
        }
        log.info("item.provisions_consumed initiator={} count={}", event.initiator().getName(),
                event.consumedFood().size());
    }

    private record ItemTemplateDefinition(UUID id, String name, String description, ItemType type, int weight,
            ArmorCategory armorCategory, int baseAc, String damageDice, WeaponCategory weaponCategory, int price,
            Rarity rarity, int bonus, ConsumableEffect consumableEffect, String effectDice, Integer nutritionValue) {
    }
}
