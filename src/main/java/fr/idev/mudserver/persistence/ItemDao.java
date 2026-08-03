package fr.idev.mudserver.persistence;

import static fr.idev.mudserver.persistence.jooq.Tables.ITEM;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import fr.idev.mudserver.domain.EquipmentSlot;
import fr.idev.mudserver.domain.Item;
import fr.idev.mudserver.persistence.jooq.tables.records.ItemRecord;

@Repository
public class ItemDao {

    private final DSLContext dsl;

    public ItemDao(DSLContext dsl) {
        this.dsl = dsl;
    }

    public void insert(Item item) {
        dsl.insertInto(ITEM, ITEM.ID, ITEM.TEMPLATE_ID, ITEM.ROOM_ID, ITEM.CHARACTER_ID, ITEM.SLOT)
                .values(item.getId(), item.getTemplateId(), item.getRoomId(), item.getCharacterId(),
                        item.getSlot() == null ? null : item.getSlot().name())
                .execute();
    }

    public Optional<Item> findById(UUID id) {
        return dsl.selectFrom(ITEM).where(ITEM.ID.eq(id)).fetchOptional(ItemDao::toDomain);
    }

    public List<Item> findByRoomId(UUID roomId) {
        return dsl.selectFrom(ITEM).where(ITEM.ROOM_ID.eq(roomId)).fetch(ItemDao::toDomain);
    }

    public List<Item> findByCharacterId(UUID characterId) {
        return dsl.selectFrom(ITEM).where(ITEM.CHARACTER_ID.eq(characterId)).fetch(ItemDao::toDomain);
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

    private static Item toDomain(ItemRecord record) {
        String slot = record.getSlot();
        return new Item(record.getId(), record.getTemplateId(), record.getRoomId(), record.getCharacterId(),
                slot == null ? null : EquipmentSlot.valueOf(slot));
    }
}
