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
 * Non-régression : Character#equipItem publie un seul événement
 * (CharacterEquippedItem) portant à la fois le nouvel item et l'éventuel
 * occupant précédent du slot, pour que le listener
 * ItemService#onCharacterEquippedItem (@Transactional, voir ItemService.java)
 * applique les deux updateSlot dans une même transaction, comme le suppose la
 * contrainte différée uniq_character_slot de V1__init_schema.sql.
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

    @Autowired
    private RoomService roomService;

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
        firstSword.attachTemplate(weaponTemplate);
        itemDao.insert(firstSword);
        character.addItem(firstSword);

        Item secondSword = new Item(UUID.randomUUID(), weaponTemplate.getId(), null, character.getId(), null);
        secondSword.attachTemplate(weaponTemplate);
        itemDao.insert(secondSword);
        character.addItem(secondSword);

        assertThat(character.equipItem(firstSword)).contains(EquipmentSlot.WEAPON);
        assertThat(character.equipItem(secondSword)).contains(EquipmentSlot.WEAPON);

        assertThat(itemDao.findById(firstSword.getId())).map(Item::getSlot).isEmpty();
        assertThat(itemDao.findById(secondSword.getId())).map(Item::getSlot).contains(EquipmentSlot.WEAPON);
    }

    @Test
    void loadInventoryAttachesTemplatesAndFeedsTheCharacterCache() {
        ItemTemplate potionTemplate = new ItemTemplate(UUID.randomUUID(), "Potion de soin", null, ItemType.POTION, 1);
        itemTemplateDao.insert(potionTemplate);
        itemService.warmItemTemplates();

        Room room = new Room(UUID.randomUUID(), "Salle B", "...", true);
        roomDao.insert(room);
        Account account = new Account(UUID.randomUUID(), "fay", "hashed-password", null);
        accountDao.insert(account);
        Character character = new Character(UUID.randomUUID(), account.getId(), "Fay", room.getId(), Race.HUMAN, 10, 10,
                10, 10, 10, 10, 10, 10, 10, 10);
        characterDao.insert(character);

        Item potion = new Item(UUID.randomUUID(), potionTemplate.getId(), null, character.getId(), null);
        itemDao.insert(potion);

        character.setInventory(itemService.loadInventory(character));

        assertThat(character.getInventory()).extracting(Item::getName).containsExactly("Potion de soin");
        assertThat(itemService.findItemByName(character, "potion de soin")).map(Item::getId).contains(potion.getId());
    }

    @Test
    void addAndRemoveItemFromInventoryKeepTheCharacterAndRoomCachesInSync() {
        ItemTemplate template = new ItemTemplate(UUID.randomUUID(), "Torche", null, ItemType.MISC, 1);
        itemTemplateDao.insert(template);
        itemService.warmItemTemplates();

        Room room = new Room(UUID.randomUUID(), "Salle C", "...", null);
        roomDao.insert(room);
        roomService.warmRooms();
        Account account = new Account(UUID.randomUUID(), "gus", "hashed-password", null);
        accountDao.insert(account);
        Character character = new Character(UUID.randomUUID(), account.getId(), "Gus", room.getId(), Race.HUMAN, 10, 10,
                10, 10, 10, 10, 10, 10, 10, 10);
        characterDao.insert(character);
        roomService.room(room.getId()).join(character);

        Item torch = new Item(UUID.randomUUID(), template.getId(), room.getId(), null, null);
        itemDao.insert(torch);
        itemService.warmRoomItems(roomService.allRooms());
        Item torchWithTemplate = itemService.findItemInRoomByName(room.getId(), "Torche").orElseThrow();

        assertThat(character.pickUpItem(torchWithTemplate)).isTrue();
        assertThat(character.getInventory()).containsExactly(torchWithTemplate);
        assertThat(roomService.room(room.getId()).getItems()).isEmpty();

        character.dropItem(torchWithTemplate);
        assertThat(character.getInventory()).isEmpty();
        assertThat(roomService.room(room.getId()).getItems()).containsExactly(torchWithTemplate);
    }

    @Test
    void warmRoomItemsAttachesTemplatesToItemsOnTheGround() {
        ItemTemplate template = new ItemTemplate(UUID.randomUUID(), "Bouclier", null, ItemType.ARMOR, 4);
        itemTemplateDao.insert(template);
        itemService.warmItemTemplates();

        Room room = new Room(UUID.randomUUID(), "Salle D", "...", null);
        roomDao.insert(room);
        roomService.warmRooms();

        Item shield = new Item(UUID.randomUUID(), template.getId(), room.getId(), null, null);
        itemDao.insert(shield);

        itemService.warmRoomItems(roomService.allRooms());

        assertThat(roomService.room(room.getId()).getItems()).extracting(Item::getName).containsExactly("Bouclier");
    }
}
