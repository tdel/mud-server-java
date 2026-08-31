package app.persistence.listener;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import app.domain.actor.instance.CharacterInstance;
import app.domain.item.Item;
import app.domain.actor.event.CharacterLootedItem;
import app.domain.actor.event.GamePlayerEquippedItem;
import app.domain.actor.event.GamePlayerUnequippedItem;
import app.domain.actor.event.GamePlayerUsedManaPotion;
import app.domain.actor.event.GamePlayerUsedPotion;
import app.domain.actor.event.ItemDiscarded;
import app.domain.actor.event.ItemPurchased;
import app.network.message.ingame.EquipmentLooted;
import app.network.message.ingame.ItemBought;
import app.persistence.ItemDao;

@Service
public class ItemPersistenceListener {

    private static final Logger log = LoggerFactory.getLogger(ItemPersistenceListener.class);

    private final ItemDao itemDao;

    public ItemPersistenceListener(ItemDao itemDao) {
        this.itemDao = itemDao;
    }

    public List<Item> loadInventory(CharacterInstance character) {
        return itemDao.findByCharacter(character);
    }

    @EventListener
    void onItemDiscarded(ItemDiscarded event) {
        itemDao.delete(event.item().getId());
        log.info("item.discarded item={} template={} character={}", event.item().getId(), event.item().getTemplateId(),
                event.character().getName());
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
        event.character().send(new EquipmentLooted(event.item().getName(), event.item().getGrade()));
        log.info("item.looted item={} character={}", event.item().getName(), event.character().getName());
    }

    @EventListener
    void onItemPurchased(ItemPurchased event) {
        itemDao.insert(event.item());
        event.character().send(new ItemBought(event.item().getName(), event.item().getGrade(), event.price()));
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
    void onGamePlayerUsedManaPotion(GamePlayerUsedManaPotion event) {
        itemDao.delete(event.item().getId());
        log.info("item.consumed item={} character={} restoredAmount={}", event.item().getId(),
                event.character().getName(), event.restoredAmount());
    }
}
