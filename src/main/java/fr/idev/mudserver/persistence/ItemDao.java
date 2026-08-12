package fr.idev.mudserver.persistence;

import static fr.idev.mudserver.persistence.jooq.Tables.ITEM;

import java.util.List;
import java.util.UUID;

import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import fr.idev.mudserver.domain.EquipmentSlot;
import fr.idev.mudserver.domain.Item;
import fr.idev.mudserver.domain.ItemTemplate;
import fr.idev.mudserver.domain.RoomInstance;
import fr.idev.mudserver.domain.actor.GameCharacter;
import fr.idev.mudserver.game.ItemTemplateService;
import fr.idev.mudserver.persistence.jooq.tables.records.ItemRecord;

@Repository
public class ItemDao {

    private final DSLContext dsl;
    private final ItemTemplateService itemTemplateService;

    public ItemDao(DSLContext dsl, ItemTemplateService itemTemplateService) {
        this.dsl = dsl;
        this.itemTemplateService = itemTemplateService;
    }

    public void insert(Item item) {
        dsl.insertInto(ITEM, ITEM.ID, ITEM.TEMPLATE_ID, ITEM.ROOM_ID, ITEM.CHARACTER_ID, ITEM.SLOT)
                .values(item.getId(), item.getTemplateId(), item.getRoomId(), item.getCharacterId(),
                        item.getSlot() == null ? null : item.getSlot().name())
                .execute();
    }

    public List<Item> findByRoom(RoomInstance room) {
        return dsl.selectFrom(ITEM).where(ITEM.ROOM_ID.eq(room.getId())).fetch(record -> toItem(record, room, null));
    }

    public List<Item> findByCharacter(GameCharacter character) {
        return dsl.selectFrom(ITEM).where(ITEM.CHARACTER_ID.eq(character.getId()))
                .fetch(record -> toItem(record, null, character));
    }

    public void assignToCharacter(UUID itemId, UUID characterId) {
        dsl.update(ITEM).set(ITEM.CHARACTER_ID, characterId).setNull(ITEM.ROOM_ID).setNull(ITEM.SLOT)
                .where(ITEM.ID.eq(itemId)).execute();
    }

    public void assignToRoom(UUID itemId, UUID roomId) {
        dsl.update(ITEM).set(ITEM.ROOM_ID, roomId).setNull(ITEM.CHARACTER_ID).setNull(ITEM.SLOT)
                .where(ITEM.ID.eq(itemId)).execute();
    }

    public void updateSlot(UUID itemId, EquipmentSlot slot) {
        dsl.update(ITEM).set(ITEM.SLOT, slot == null ? null : slot.name()).where(ITEM.ID.eq(itemId)).execute();
    }

    public void delete(UUID itemId) {
        dsl.deleteFrom(ITEM).where(ITEM.ID.eq(itemId)).execute();
    }

    private Item toItem(ItemRecord record, RoomInstance room, GameCharacter character) {
        ItemTemplate template = itemTemplateService.getById(record.getTemplateId());
        String slot = record.getSlot();
        return new Item(record.getId(), template, room, character, slot == null ? null : EquipmentSlot.valueOf(slot));
    }
}
