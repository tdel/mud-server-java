package fr.idev.mudserver.game;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import fr.idev.mudserver.domain.Character;
import fr.idev.mudserver.domain.Item;
import fr.idev.mudserver.domain.ItemTemplate;
import fr.idev.mudserver.domain.ItemType;
import fr.idev.mudserver.domain.Room;
import fr.idev.mudserver.domain.event.CharacterDroppedItem;
import fr.idev.mudserver.domain.event.CharacterEquippedItem;
import fr.idev.mudserver.domain.event.CharacterUnequippedItem;
import fr.idev.mudserver.domain.event.ItemPickedUp;
import fr.idev.mudserver.persistence.ItemDao;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Cache de lecture pour les items d'un personnage — le sac comme les
 * emplacements équipés — et ceux posés au sol dans une room, et point de
 * persistance réactif pour leurs mutations : ramassage, dépôt, équipement et
 * déséquipement vivent tous désormais sur {@code Character}
 * ({@link Character#pickUpItem}/{@link Character#dropItem}/
 * {@link Character#equipItem}/{@link Character#unequipItem}), qui publie un
 * événement de domaine après chaque mutation en mémoire ; les méthodes
 * {@code @EventListener} de cette classe répercutent chacune en base via
 * {@link ItemDao}. Précharge aussi l'ensemble des {@link ItemTemplate} en
 * mémoire ({@link #warmItemTemplates()}) depuis {@code data/items.json}, sur le
 * même principe que {@code RaceService.warmRaceBonuses()} : les templates sont
 * une donnée de règles statique, jamais mutée en jeu, donc jamais persistée en
 * DB. Attache le template correspondant à chaque {@code Item} avant de le
 * renvoyer à l'appelant — un {@code Item} sorti d'ici a donc toujours son
 * template, contrairement à un {@code Item} lu directement via {@link ItemDao}.
 */
@Service
public class ItemService {

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
                registerTemplate(new ItemTemplate(definition.id(), definition.name(), definition.description(),
                        definition.type(), definition.weight()));
            }
        } catch (IOException | JacksonException e) {
            throw new IllegalStateException("Impossible de charger " + ITEM_TEMPLATE_RESOURCE, e);
        }
    }

    void registerTemplate(ItemTemplate template) {
        templates.put(template.getId(), template);
    }

    public List<Item> loadInventory(Character character) {
        List<Item> items = attachTemplates(itemDao.findByCharacterId(character.getId()));
        items.forEach(item -> item.attachCharacter(character));
        return items;
    }

    /**
     * Précharge les items au sol de chaque room, une fois pour toute la durée du
     * process — appelé depuis {@code TelnetServer.start()} juste après
     * {@code RoomService.warmRooms()} et {@link #warmItemTemplates()}, sur le même
     * principe : une {@code Room} n'est jamais rechargée par session, contrairement
     * à un {@code Character}.
     */
    public void warmRoomItems(Collection<Room> rooms) {
        for (Room room : rooms) {
            List<Item> items = attachTemplates(itemDao.findByRoomId(room.getId()));
            items.forEach(item -> item.attachRoom(room));
            room.setItems(items);
        }
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
    }

    @EventListener
    void onCharacterDroppedItem(CharacterDroppedItem event) {
        itemDao.assignToRoom(event.item().getId(), event.room().getId());
    }

    /**
     * {@code @Transactional} pour de vrai ici : les deux {@code updateSlot}
     * (déséquiper l'ancien occupant du slot, équiper le nouveau) doivent partager
     * une transaction pour que la contrainte différée
     * {@code uniq_character_slot DEFERRABLE INITIALLY DEFERRED} (voir
     * V1__init_schema.sql) protège réellement le chevauchement transitoire entre
     * les deux UPDATE — sans transaction commune, chaque UPDATE valide
     * immédiatement en autocommit et la déférence de la contrainte ne sert à rien.
     */
    @EventListener
    @Transactional
    void onCharacterEquippedItem(CharacterEquippedItem event) {
        for (Item previousOccupant : event.previousOccupants()) {
            itemDao.updateSlot(previousOccupant.getId(), null);
        }
        itemDao.updateSlot(event.item().getId(), event.slot());
    }

    @EventListener
    void onCharacterUnequippedItem(CharacterUnequippedItem event) {
        itemDao.updateSlot(event.item().getId(), null);
    }

    private record ItemTemplateDefinition(UUID id, String name, String description, ItemType type, int weight) {
    }
}
