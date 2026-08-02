package fr.idev.mudserver.game;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import fr.idev.mudserver.AbstractIntegrationTest;
import fr.idev.mudserver.domain.Account;
import fr.idev.mudserver.domain.Character;
import fr.idev.mudserver.domain.EquipmentSlot;
import fr.idev.mudserver.domain.Item;
import fr.idev.mudserver.domain.ItemTemplate;
import fr.idev.mudserver.domain.ItemType;
import fr.idev.mudserver.domain.Race;
import fr.idev.mudserver.domain.Room;
import fr.idev.mudserver.persistence.AccountDao;
import fr.idev.mudserver.persistence.CharacterDao;
import fr.idev.mudserver.persistence.ItemDao;
import fr.idev.mudserver.persistence.ItemTemplateDao;
import fr.idev.mudserver.persistence.RoomDao;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Non-régression migration jOOQ : ItemService#equipItem est désormais
 * {@code @Transactional} (voir ItemService.java) pour que les deux updateSlot
 * (déséquiper l'ancien occupant, équiper le nouveau) partagent une transaction,
 * comme le suppose la contrainte différée uniq_character_slot de
 * V1__init_schema.sql.
 */
@Transactional
class ItemServiceTest extends AbstractIntegrationTest {

    @Autowired
    private ItemService itemService;

    @Autowired
    private ItemDao itemDao;

    @Autowired
    private ItemTemplateDao itemTemplateDao;

    @Autowired
    private RoomDao roomDao;

    @Autowired
    private AccountDao accountDao;

    @Autowired
    private CharacterDao characterDao;

    @Test
    void equippingANewWeaponUnequipsThePreviousOneInTheSameTransaction() {
        ItemTemplate weaponTemplate = new ItemTemplate(UUID.randomUUID(), "Épée", null, ItemType.WEAPON, 3);
        itemTemplateDao.insert(weaponTemplate);

        Room room = new Room(UUID.randomUUID(), "Salle A", "...", true);
        roomDao.insert(room);
        Account account = new Account(UUID.randomUUID(), "erin", "hashed-password", null);
        accountDao.insert(account);
        Character character = new Character(UUID.randomUUID(), account.getId(), "Erin", room.getId(), Race.HUMAN, 10,
                10, 10, 10, 10, 10, 10, 10, 10, 10);
        characterDao.insert(character);

        Item firstSword = new Item(UUID.randomUUID(), weaponTemplate.getId(), null, character.getId(), null);
        itemDao.insert(firstSword);
        Item secondSword = new Item(UUID.randomUUID(), weaponTemplate.getId(), null, character.getId(), null);
        itemDao.insert(secondSword);

        assertThat(itemService.equipItem(firstSword, character)).contains(EquipmentSlot.WEAPON);
        assertThat(itemService.equipItem(secondSword, character)).contains(EquipmentSlot.WEAPON);

        assertThat(itemDao.findById(firstSword.getId())).map(Item::getSlot).isEmpty();
        assertThat(itemDao.findById(secondSword.getId())).map(Item::getSlot).contains(EquipmentSlot.WEAPON);
    }
}
