package fr.idev.mudserver.game;

import java.util.Collection;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import fr.idev.mudserver.domain.actor.GamePlayer;
import fr.idev.mudserver.domain.Item;
import fr.idev.mudserver.domain.RoomInstance;
import fr.idev.mudserver.domain.actor.event.CharacterLootedItem;
import fr.idev.mudserver.domain.actor.event.GamePlayerDroppedItem;
import fr.idev.mudserver.domain.actor.event.GamePlayerEquippedItem;
import fr.idev.mudserver.domain.actor.event.GamePlayerUnequippedItem;
import fr.idev.mudserver.domain.actor.event.GamePlayerUsedPotion;
import fr.idev.mudserver.domain.actor.event.ItemPickedUp;
import fr.idev.mudserver.domain.actor.event.ItemPurchased;
import fr.idev.mudserver.domain.actor.event.LongRestTaken;
import fr.idev.mudserver.network.message.ingame.EquipmentLooted;
import fr.idev.mudserver.network.message.ingame.ItemBought;
import fr.idev.mudserver.persistence.ItemDao;

@Service
public class ItemService {

    private static final Logger log = LoggerFactory.getLogger(ItemService.class);

    private final ItemDao itemDao;

    public ItemService(ItemDao itemDao) {
        this.itemDao = itemDao;
    }

    public List<Item> loadInventory(GamePlayer character) {
        return itemDao.findByCharacter(character);
    }

    public void warmRoomItems(Collection<RoomInstance> rooms) {
        int totalItems = 0;
        for (RoomInstance room : rooms) {
            List<Item> items = itemDao.findByRoom(room);
            room.setItems(items);
            totalItems += items.size();
        }
        log.info("item.room_items_loaded count={} rooms={}", totalItems, rooms.size());
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
        itemDao.insert(event.item());
        event.character().send(new EquipmentLooted(event.item().getName(), event.item().getRarity()));
        log.info("item.looted item={} character={}", event.item().getName(), event.character().getName());
    }

    @EventListener
    void onItemPurchased(ItemPurchased event) {
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
}
