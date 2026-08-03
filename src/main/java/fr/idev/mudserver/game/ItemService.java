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
import fr.idev.mudserver.domain.EquipmentSlot;
import fr.idev.mudserver.domain.Item;
import fr.idev.mudserver.domain.ItemTemplate;
import fr.idev.mudserver.domain.Room;
import fr.idev.mudserver.domain.event.ItemPickedUp;
import fr.idev.mudserver.persistence.ItemDao;
import fr.idev.mudserver.persistence.ItemTemplateDao;

/**
 * Point d'entrée unique pour lire et muter les items d'un personnage — le sac
 * comme les emplacements équipés — et ceux posés au sol dans une room. Toute
 * nouvelle mutation d'inventaire (take/drop/equip/unequip, un futur
 * craft/loot/trade) doit passer par ici plutôt que par {@link ItemDao}
 * directement. Précharge aussi l'ensemble des {@link ItemTemplate} en mémoire
 * ({@link #warmItemTemplates()}, sur le même principe que
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

    /**
     * Charge l'inventaire de {@code character} depuis la base, une fois, pour
     * l'attacher au personnage (voir {@code GameWorld.enterWorld}) — les lectures
     * suivantes ({@link #getInventory}, {@link #getCarriedItems},
     * {@link #getEquippedItems}) passent ensuite par le cache du personnage plutôt
     * que par une nouvelle requête.
     */
    public List<Item> loadInventory(Character character) {
        return attachTemplates(itemDao.findByCharacterId(character.getId()));
    }

    public List<Item> getInventory(Character character) {
        return character.getInventory();
    }

    public List<Item> getCarriedItems(Character character) {
        return character.getCarriedItems();
    }

    public List<Item> getEquippedItems(Character character) {
        return character.getEquippedItems();
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

    /**
     * Fait passer {@code item} dans le sac de {@code target}, sauf si un autre
     * joueur l'a déjà pris entre-temps. Deux joueurs peuvent réellement se disputer
     * un item non possédé sous les virtual threads — cette méthode relit la ligne
     * sous verrou pessimiste (dans une transaction) avant de décider si l'item est
     * encore libre. Voir {@link ItemDao#findByIdForUpdate}. Une fois la course
     * tranchée, la mutation (domaine + DB) est déléguée à
     * {@link Character#pickUpItem} et à son événement — jamais avant, pour ne pas
     * rouvrir la fenêtre de course que le verrou vient de fermer.
     *
     * @return true si {@code target} porte désormais l'item, false s'il a été pris
     *         entre-temps
     */
    @Transactional
    public boolean addItemToInventory(Item item, Character target) {
        Item locked = itemDao.findByIdForUpdate(item.getId()).orElseThrow();

        if (locked.getCharacterId() != null) {
            return false;
        }

        target.pickUpItem(item);
        return true;
    }

    @EventListener
    void onItemPickedUp(ItemPickedUp event) {
        itemDao.assignToCharacter(event.item().getId(), event.character().getId());
    }

    public void removeItemFromInventory(Item item, Character target) {
        itemDao.assignToRoom(item.getId(), target.getCurrentRoomId());
        item.assignToRoom(target.getCurrentRoomId());
        target.removeItem(item);
        roomService.room(target.getCurrentRoomId()).addItem(item);
    }

    /**
     * {@code @Transactional} pour de vrai ici, contrairement à avant : les deux
     * {@code updateSlot} (déséquiper l'ancien occupant du slot, équiper le nouveau)
     * doivent partager une transaction pour que la contrainte différée
     * {@code uniq_character_slot DEFERRABLE INITIALLY DEFERRED} (voir
     * V1__init_schema.sql) protège réellement le chevauchement transitoire entre
     * les deux UPDATE — sans transaction commune, chaque UPDATE valide
     * immédiatement en autocommit et la déférence de la contrainte ne sert à rien.
     */
    @Transactional
    public Optional<EquipmentSlot> equipItem(Item item, Character target) {
        Optional<EquipmentSlot> slot = item.getType().equipmentSlot();

        if (slot.isEmpty()) {
            return Optional.empty();
        }

        for (Item existing : target.getEquippedItems()) {
            if (!existing.getId().equals(item.getId()) && existing.getSlot() == slot.get()) {
                itemDao.updateSlot(existing.getId(), null);
                existing.setSlot(null);
            }
        }

        itemDao.updateSlot(item.getId(), slot.get());
        item.setSlot(slot.get());
        return slot;
    }

    public void unequipItem(Item item) {
        itemDao.updateSlot(item.getId(), null);
        item.setSlot(null);
    }
}
