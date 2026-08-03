package fr.idev.mudserver.persistence;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import fr.idev.mudserver.AbstractIntegrationTest;
import fr.idev.mudserver.domain.ItemTemplate;
import fr.idev.mudserver.domain.ItemType;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class ItemTemplateDaoTest extends AbstractIntegrationTest {

    @Autowired
    private ItemTemplateDao itemTemplateDao;

    @Test
    void insertsAndFindsById() {
        ItemTemplate template = new ItemTemplate(UUID.randomUUID(), "Épée courte", "Une épée légère", ItemType.WEAPON,
                3);

        itemTemplateDao.insert(template);

        assertThat(itemTemplateDao.findById(template.getId())).contains(template);
    }

    @Test
    void findsByName() {
        ItemTemplate template = new ItemTemplate(UUID.randomUUID(), "Potion de soin", null, ItemType.POTION, 1);
        itemTemplateDao.insert(template);

        assertThat(itemTemplateDao.findByName("Potion de soin")).contains(template);
        assertThat(itemTemplateDao.findByName("inconnu")).isEmpty();
    }

    @Test
    void existsByIdReflectsInsertedRows() {
        UUID id = UUID.randomUUID();
        assertThat(itemTemplateDao.existsById(id)).isFalse();

        itemTemplateDao.insert(new ItemTemplate(id, "Casque", null, ItemType.HELMET, 2));

        assertThat(itemTemplateDao.existsById(id)).isTrue();
    }

    @Test
    void findAllReturnsEveryTemplate() {
        ItemTemplate first = new ItemTemplate(UUID.randomUUID(), "Bouclier", null, ItemType.ARMOR, 4);
        ItemTemplate second = new ItemTemplate(UUID.randomUUID(), "Bottes", null, ItemType.BOOTS, 2);
        itemTemplateDao.insert(first);
        itemTemplateDao.insert(second);

        assertThat(itemTemplateDao.findAll()).contains(first, second);
    }
}
