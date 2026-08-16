package fr.idev.mudserver.persistence;

import static fr.idev.mudserver.persistence.jooq.Tables.ITEM;

import java.util.List;
import java.util.UUID;

import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import fr.idev.mudserver.domain.item.EquipmentSlot;
import fr.idev.mudserver.domain.item.Item;
import fr.idev.mudserver.domain.item.ItemTemplate;
import fr.idev.mudserver.domain.actor.AbstractCharacter;
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
        dsl.insertInto(ITEM, ITEM.ID, ITEM.TEMPLATE_ID, ITEM.CHARACTER_ID, ITEM.SLOT).values(item.getId(),
                item.getTemplateId(), item.getCharacterId(), item.getSlot() == null ? null : item.getSlot().name())
                .execute();
    }

    public List<Item> findByCharacter(AbstractCharacter character) {
        return dsl.selectFrom(ITEM).where(ITEM.CHARACTER_ID.eq(character.getId()))
                .fetch(record -> toItem(record, character));
    }

    public void assignToCharacter(UUID itemId, UUID characterId) {
        dsl.update(ITEM).set(ITEM.CHARACTER_ID, characterId).setNull(ITEM.SLOT).where(ITEM.ID.eq(itemId)).execute();
    }

    public void updateSlot(UUID itemId, EquipmentSlot slot) {
        dsl.update(ITEM).set(ITEM.SLOT, slot == null ? null : slot.name()).where(ITEM.ID.eq(itemId)).execute();
    }

    public void delete(UUID itemId) {
        dsl.deleteFrom(ITEM).where(ITEM.ID.eq(itemId)).execute();
    }

    private Item toItem(ItemRecord record, AbstractCharacter character) {
        ItemTemplate template = itemTemplateService.getById(record.getTemplateId());
        String slot = record.getSlot();
        return new Item(record.getId(), template, character, slot == null ? null : EquipmentSlot.valueOf(slot));
    }
}
