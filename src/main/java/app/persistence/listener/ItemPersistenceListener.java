package app.persistence.listener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import app.domain.item.Item;
import app.domain.actor.event.CharacterLootedItem;
import app.domain.actor.event.GamePlayerEquippedItem;
import app.domain.actor.event.GamePlayerUnequippedItem;
import app.domain.actor.event.GamePlayerUsedManaPotion;
import app.domain.actor.event.GamePlayerUsedPotion;
import app.domain.actor.event.ItemDiscarded;
import app.domain.actor.event.ItemPurchased;
import app.domain.actor.event.ShotActivated;
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

    @EventListener
    void onItemDiscarded(ItemDiscarded event) {
        itemDao.delete(event.item().getId());
        log.info("dao.item.ItemDiscarded object={} [template={}, character={}]", event.item().getId(),
                event.item().getTemplateId(), event.character().getName());
    }

    @EventListener
    @Transactional
    void onGamePlayerEquippedItem(GamePlayerEquippedItem event) {
        for (Item previousOccupant : event.previousOccupants()) {
            itemDao.updateSlot(previousOccupant.getId(), null);
        }
        itemDao.updateSlot(event.item().getId(), event.slot());
        log.info("dao.item.GamePlayerEquippedItem object={} [slot={}, character={}, previousOccupants={}]",
                event.item().getId(), event.slot(), event.character().getName(), event.previousOccupants().size());
    }

    @EventListener
    void onGamePlayerUnequippedItem(GamePlayerUnequippedItem event) {
        itemDao.updateSlot(event.item().getId(), null);
        log.info("dao.item.GamePlayerUnequippedItem object={} [character={}]", event.item().getId(),
                event.character().getName());
    }

    @EventListener
    void onCharacterLootedItem(CharacterLootedItem event) {
        if (event.merged()) {
            itemDao.updateQuantity(event.item().getId(), event.item().getQuantity());
        } else {
            itemDao.insert(event.item());
        }
        event.character().send(new EquipmentLooted(event.item().getName(), event.item().getGrade()));
        log.info("dao.item.CharacterLootedItem object={} [character={}, merged={}]", event.item().getId(),
                event.character().getName(), event.merged());
    }

    @EventListener
    void onItemPurchased(ItemPurchased event) {
        if (event.merged()) {
            itemDao.updateQuantity(event.item().getId(), event.item().getQuantity());
        } else {
            itemDao.insert(event.item());
        }
        event.character().send(new ItemBought(event.item().getName(), event.item().getGrade(), event.price()));
        log.info("dao.item.ItemPurchased object={} [character={}, price={}, merged={}]", event.item().getId(),
                event.character().getName(), event.price(), event.merged());
    }

    @EventListener
    void onShotActivated(ShotActivated event) {
        if (event.remainingQuantity() <= 0) {
            itemDao.delete(event.item().getId());
        } else {
            itemDao.updateQuantity(event.item().getId(), event.remainingQuantity());
        }
        log.info("dao.item.ShotActivated object={} [character={}, shotType={}, grade={}, remaining={}]",
                event.item().getId(), event.character().getName(), event.shotType(), event.grade(),
                event.remainingQuantity());
    }

    @EventListener
    void onGamePlayerUsedPotion(GamePlayerUsedPotion event) {
        itemDao.delete(event.item().getId());
        log.info("dao.item.GamePlayerUsedPotion object={} [character={}, healedAmount={}]", event.item().getId(),
                event.character().getName(), event.healedAmount());
    }

    @EventListener
    void onGamePlayerUsedManaPotion(GamePlayerUsedManaPotion event) {
        itemDao.delete(event.item().getId());
        log.info("dao.item.GamePlayerUsedManaPotion object={} [character={}, restoredAmount={}]", event.item().getId(),
                event.character().getName(), event.restoredAmount());
    }
}
