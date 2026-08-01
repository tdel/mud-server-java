package fr.idev.mudserver.game;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import fr.idev.mudserver.domain.Character;
import fr.idev.mudserver.domain.EquipmentSlot;
import fr.idev.mudserver.domain.Item;
import fr.idev.mudserver.domain.ItemTemplate;
import fr.idev.mudserver.persistence.ItemDao;
import fr.idev.mudserver.persistence.ItemTemplateDao;

/**
 * Point d'entrée unique pour lire et muter les items d'un personnage — le sac comme les
 * emplacements équipés. Toute nouvelle mutation d'inventaire (take/drop/equip/unequip, un
 * futur craft/loot/trade) doit passer par ici plutôt que par {@link ItemDao} directement.
 */
@Service
public class ItemService {

    private final ItemDao itemDao;
    private final ItemTemplateDao itemTemplateDao;

    public ItemService(ItemDao itemDao, ItemTemplateDao itemTemplateDao) {
        this.itemDao = itemDao;
        this.itemTemplateDao = itemTemplateDao;
    }

    public List<Item> getInventory(Character character) {
        return itemDao.findByCharacterId(character.id());
    }

    public List<Item> getCarriedItems(Character character) {
        return getInventory(character).stream().filter(item -> item.slot() == null).toList();
    }

    public List<Item> getEquippedItems(Character character) {
        return getInventory(character).stream().filter(item -> item.slot() != null).toList();
    }

    public Optional<Item> findItemByName(Character character, String name) {
        return findByTemplateName(getInventory(character), name);
    }

    public Optional<Item> findItemInRoomByName(UUID roomId, String name) {
        return findByTemplateName(itemDao.findByRoomId(roomId), name);
    }

    private Optional<Item> findByTemplateName(List<Item> items, String name) {
        for (Item item : items) {
            String templateName = itemTemplateDao.findById(item.templateId()).map(ItemTemplate::name).orElseThrow();
            if (templateName.equalsIgnoreCase(name)) {
                return Optional.of(item);
            }
        }
        return Optional.empty();
    }

    /**
     * Fait passer {@code item} dans le sac de {@code target}, sauf si un autre joueur l'a déjà
     * pris entre-temps. Deux joueurs peuvent réellement se disputer un item non possédé sous
     * les virtual threads — cette méthode relit la ligne sous verrou pessimiste (dans une
     * transaction) avant de décider si l'item est encore libre. Voir
     * {@link ItemDao#findByIdForUpdate}.
     *
     * @return true si {@code target} porte désormais l'item, false s'il a été pris entre-temps
     */
    @Transactional
    public boolean addItemToInventory(Item item, Character target) {
        Item locked = itemDao.findByIdForUpdate(item.id()).orElseThrow();

        if (locked.characterId() != null) {
            return false;
        }

        itemDao.assignToCharacter(item.id(), target.id());
        return true;
    }

    public void removeItemFromInventory(Item item, Character target) {
        itemDao.assignToRoom(item.id(), target.currentRoomId());
    }

    public Optional<EquipmentSlot> equipItem(Item item, Character target) {
        ItemTemplate template = itemTemplateDao.findById(item.templateId()).orElseThrow();
        Optional<EquipmentSlot> slot = template.type().equipmentSlot();

        if (slot.isEmpty()) {
            return Optional.empty();
        }

        for (Item existing : getEquippedItems(target)) {
            if (!existing.id().equals(item.id()) && existing.slot() == slot.get()) {
                itemDao.updateSlot(existing.id(), null);
            }
        }

        itemDao.updateSlot(item.id(), slot.get());
        return slot;
    }

    public void unequipItem(Item item) {
        itemDao.updateSlot(item.id(), null);
    }
}
