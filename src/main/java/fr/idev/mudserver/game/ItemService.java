package fr.idev.mudserver.game;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import fr.idev.mudserver.domain.Character;
import fr.idev.mudserver.domain.Item;
import fr.idev.mudserver.domain.ItemTemplate;
import fr.idev.mudserver.domain.Room;
import fr.idev.mudserver.domain.event.CharacterDroppedItem;
import fr.idev.mudserver.domain.event.CharacterEquippedItem;
import fr.idev.mudserver.domain.event.CharacterUnequippedItem;
import fr.idev.mudserver.domain.event.ItemPickedUp;
import fr.idev.mudserver.persistence.ItemDao;
import fr.idev.mudserver.persistence.ItemTemplateDao;

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
 * mémoire ({@link #warmItemTemplates()}, sur le même principe que
 * {@code RoomService.warmRooms()}) et attache le template correspondant à
 * chaque {@code Item} avant de le renvoyer à l'appelant — un {@code Item} sorti
 * d'ici a donc toujours son template, contrairement à un {@code Item} lu
 * directement via {@link ItemDao}.
 */
@Service
public class ItemService {

    private final Map<UUID, ItemTemplate> templates = new ConcurrentHashMap<>();

    private final ItemDao itemDao;
    private final ItemTemplateDao itemTemplateDao;
    private final RoomService roomService;

    public ItemService(ItemDao itemDao, ItemTemplateDao itemTemplateDao, RoomService roomService) {
        this.itemDao = itemDao;
        this.itemTemplateDao = itemTemplateDao;
        this.roomService = roomService;
    }

    public void warmItemTemplates() {
        for (ItemTemplate template : itemTemplateDao.findAll()) {
            templates.put(template.getId(), template);
        }
    }

    private Item attachTemplate(Item item) {
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

    public List<Item> loadInventory(Character character) {
        return attachTemplates(itemDao.findByCharacterId(character.getId()));
    }

    public List<Item> getInventory(Character character) {
        return character.getInventory();
    }

    /**
     * Charge les items au sol de {@code room} depuis la base, une fois, pour les
     * attacher à la room (voir {@link #warmRoomItems}) — {@link #findRoomItems}
     * passe ensuite par le cache de la room plutôt que par une nouvelle requête.
     */
    public List<Item> loadRoomItems(Room room) {
        return attachTemplates(itemDao.findByRoomId(room.getId()));
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
            room.setItems(loadRoomItems(room));
        }
    }

    public List<Item> findRoomItems(UUID roomId) {
        return roomService.room(roomId).getItems();
    }

    public Optional<Item> findItemByName(Character character, String name) {
        return findByTemplateName(character.getInventory(), name);
    }

    public Optional<Item> findItemInRoomByName(UUID roomId, String name) {
        return findByTemplateName(findRoomItems(roomId), name);
    }

    private Optional<Item> findByTemplateName(List<Item> items, String name) {
        return items.stream().filter(item -> item.getName().equalsIgnoreCase(name)).findFirst();
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
}
