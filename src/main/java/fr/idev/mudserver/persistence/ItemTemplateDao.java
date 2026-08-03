package fr.idev.mudserver.persistence;

import static fr.idev.mudserver.persistence.jooq.Tables.ITEM_TEMPLATE;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import fr.idev.mudserver.domain.ItemTemplate;
import fr.idev.mudserver.domain.ItemType;
import fr.idev.mudserver.persistence.jooq.tables.records.ItemTemplateRecord;

@Repository
public class ItemTemplateDao {

    private final DSLContext dsl;

    public ItemTemplateDao(DSLContext dsl) {
        this.dsl = dsl;
    }

    public void insert(ItemTemplate template) {
        dsl.insertInto(ITEM_TEMPLATE, ITEM_TEMPLATE.ID, ITEM_TEMPLATE.NAME, ITEM_TEMPLATE.DESCRIPTION,
                ITEM_TEMPLATE.TYPE, ITEM_TEMPLATE.WEIGHT)
                .values(template.getId(), template.getName(), template.getDescription(), template.getType().name(),
                        template.getWeight())
                .execute();
    }

    public Optional<ItemTemplate> findById(UUID id) {
        return dsl.selectFrom(ITEM_TEMPLATE).where(ITEM_TEMPLATE.ID.eq(id)).fetchOptional(ItemTemplateDao::toDomain);
    }

    public List<ItemTemplate> findAll() {
        return dsl.selectFrom(ITEM_TEMPLATE).fetch(ItemTemplateDao::toDomain);
    }

    public Optional<ItemTemplate> findByName(String name) {
        return dsl.selectFrom(ITEM_TEMPLATE).where(ITEM_TEMPLATE.NAME.eq(name))
                .fetchOptional(ItemTemplateDao::toDomain);
    }

    public boolean existsById(UUID id) {
        return dsl.fetchExists(dsl.selectFrom(ITEM_TEMPLATE).where(ITEM_TEMPLATE.ID.eq(id)));
    }

    private static ItemTemplate toDomain(ItemTemplateRecord record) {
        return new ItemTemplate(record.getId(), record.getName(), record.getDescription(),
                ItemType.valueOf(record.getType()), record.getWeight());
    }
}
