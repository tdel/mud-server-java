package app.persistence;

import static app.persistence.jooq.Tables.ITEM;

import java.util.List;
import java.util.UUID;

import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import app.domain.item.EquipmentSlot;
import app.domain.item.Item;
import app.domain.item.ItemTemplate;
import app.domain.actor.AbstractCharacter;
import app.game.catalog.ItemTemplateCatalog;
import app.persistence.jooq.tables.records.ItemRecord;

@Repository
public class ItemDao {

    private final DSLContext dsl;
    private final ItemTemplateCatalog itemTemplateCatalog;

    public ItemDao(DSLContext dsl, ItemTemplateCatalog itemTemplateCatalog) {
        this.dsl = dsl;
        this.itemTemplateCatalog = itemTemplateCatalog;
    }

    public void insert(Item item) {
        dsl.insertInto(ITEM, ITEM.ID, ITEM.TEMPLATE_ID, ITEM.CHARACTER_ID, ITEM.SLOT, ITEM.ENCHANT)
                .values(item.getId(), item.getTemplateId(), item.getCharacterId(),
                        item.getSlot() == null ? null : item.getSlot().name(), item.getEnchant())
                .execute();
    }

    // Utilisé pour charger l'inventaire AVANT de construire le CharacterInstance
    // propriétaire (voir CharacterDao.toDomain) : les Item obtenus n'ont pas
    // encore de character (rattaché ensuite via Item.attachOwner).
    public List<Item> findByCharacterId(UUID characterId) {
        return dsl.selectFrom(ITEM).where(ITEM.CHARACTER_ID.eq(characterId)).fetch(record -> toItem(record, null));
    }

    public void updateSlot(UUID itemId, EquipmentSlot slot) {
        dsl.update(ITEM).set(ITEM.SLOT, slot == null ? null : slot.name()).where(ITEM.ID.eq(itemId)).execute();
    }

    public void delete(UUID itemId) {
        dsl.deleteFrom(ITEM).where(ITEM.ID.eq(itemId)).execute();
    }

    private Item toItem(ItemRecord record, AbstractCharacter character) {
        ItemTemplate template = itemTemplateCatalog.getById(record.getTemplateId());
        String slot = record.getSlot();
        return new Item(record.getId(), template, character, slot == null ? null : EquipmentSlot.valueOf(slot),
                record.getEnchant());
    }
}
