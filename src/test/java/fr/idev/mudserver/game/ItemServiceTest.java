package fr.idev.mudserver.game;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import fr.idev.mudserver.AbstractIntegrationTest;
import fr.idev.mudserver.domain.Account;
import fr.idev.mudserver.domain.GamePlayer;
import fr.idev.mudserver.domain.CharacterClass;
import fr.idev.mudserver.domain.EquipmentSlot;
import fr.idev.mudserver.domain.Item;
import fr.idev.mudserver.domain.ItemTemplate;
import fr.idev.mudserver.domain.ItemType;
import fr.idev.mudserver.domain.Race;
import fr.idev.mudserver.domain.Room;
import fr.idev.mudserver.domain.TestAttributes;
import fr.idev.mudserver.persistence.AccountDao;
import fr.idev.mudserver.persistence.CharacterDao;
import fr.idev.mudserver.persistence.ItemDao;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Non-régression : GamePlayer#equipItem publie un seul événement
 * (GamePlayerEquippedItem) portant à la fois le nouvel item et l'éventuel
 * occupant précédent du slot, pour que le listener
 * ItemService#onGamePlayerEquippedItem (@Transactional, voir ItemService.java)
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
    private AccountDao accountDao;

    @Autowired
    private CharacterDao characterDao;

    @Autowired
    private RoomService roomService;

    @Test
    void equippingANewWeaponUnequipsThePreviousOneInTheSameTransaction() {
        ItemTemplate weaponTemplate = new ItemTemplate(UUID.randomUUID(), "Épée", null, ItemType.WEAPON, 3, null, 0);

        Account account = new Account(UUID.randomUUID(), "erin", "hashed-password", null);
        accountDao.insert(account);
        GamePlayer character = new GamePlayer(UUID.randomUUID(), account.getId(), "Erin", UUID.randomUUID(), Race.HUMAN,
                CharacterClass.FIGHTER, 1, 10, 10, TestAttributes.of(10, 10, 10, 10, 10, 10));
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
    void loadInventoryStillReturnsAnEquippedItemOnAFreshLoad() {
        ItemTemplate weaponTemplate = new ItemTemplate(UUID.randomUUID(), "Épée", null, ItemType.WEAPON, 3, null, 0);
        itemService.registerTemplate(weaponTemplate);

        Account account = new Account(UUID.randomUUID(), "gwen", "hashed-password", null);
        accountDao.insert(account);
        GamePlayer character = new GamePlayer(UUID.randomUUID(), account.getId(), "Gwen", UUID.randomUUID(), Race.HUMAN,
                CharacterClass.FIGHTER, 1, 10, 10, TestAttributes.of(10, 10, 10, 10, 10, 10));
        characterDao.insert(character);

        Item sword = new Item(UUID.randomUUID(), weaponTemplate.getId(), null, character.getId(), null);
        sword.attachTemplate(weaponTemplate);
        itemDao.insert(sword);
        character.addItem(sword);
        character.equipItem(sword);

        // Simule une reconnexion : on recharge l'inventaire depuis la base plutôt
        // que de lire le cache en mémoire de `character`.
        List<Item> reloadedInventory = itemService.loadInventory(character);

        assertThat(reloadedInventory).extracting(Item::getId).contains(sword.getId());
        assertThat(reloadedInventory).filteredOn(item -> item.getId().equals(sword.getId())).extracting(Item::getSlot)
                .containsExactly(EquipmentSlot.WEAPON);
    }

    @Test
    void loadInventoryAttachesTemplatesAndFeedsTheCharacterCache() {
        ItemTemplate potionTemplate = new ItemTemplate(UUID.randomUUID(), "Potion de soin", null, ItemType.POTION, 1,
                null, 0);
        itemService.registerTemplate(potionTemplate);

        Account account = new Account(UUID.randomUUID(), "fay", "hashed-password", null);
        accountDao.insert(account);
        GamePlayer character = new GamePlayer(UUID.randomUUID(), account.getId(), "Fay", UUID.randomUUID(), Race.HUMAN,
                CharacterClass.FIGHTER, 1, 10, 10, TestAttributes.of(10, 10, 10, 10, 10, 10));
        characterDao.insert(character);

        Item potion = new Item(UUID.randomUUID(), potionTemplate.getId(), null, character.getId(), null);
        itemDao.insert(potion);

        character.setInventory(itemService.loadInventory(character));

        assertThat(character.getInventory()).extracting(Item::getName).containsExactly("Potion de soin");
        assertThat(character.findOneByName("potion de soin")).map(Item::getId).contains(potion.getId());
    }

    @Test
    void addAndRemoveItemFromInventoryKeepTheCharacterAndRoomCachesInSync() {
        ItemTemplate template = new ItemTemplate(UUID.randomUUID(), "Torche", null, ItemType.MISC, 1, null, 0);
        itemService.registerTemplate(template);

        roomService.warmRooms();
        Room warmedRoom = roomService.allRooms().iterator().next();
        Account account = new Account(UUID.randomUUID(), "gus", "hashed-password", null);
        accountDao.insert(account);
        GamePlayer character = new GamePlayer(UUID.randomUUID(), account.getId(), "Gus", warmedRoom.getId(), Race.HUMAN,
                CharacterClass.FIGHTER, 1, 10, 10, TestAttributes.of(10, 10, 10, 10, 10, 10));
        characterDao.insert(character);
        warmedRoom.join(character);

        Item torch = new Item(UUID.randomUUID(), template.getId(), warmedRoom.getId(), null, null);
        itemDao.insert(torch);
        itemService.warmRoomItems(roomService.allRooms());
        Item torchWithTemplate = warmedRoom.findOneByName("Torche").orElseThrow();

        assertThat(character.pickUpItem(torchWithTemplate)).isTrue();
        assertThat(character.getInventory()).containsExactly(torchWithTemplate);
        assertThat(warmedRoom.getItems()).isEmpty();

        character.dropItem(torchWithTemplate);
        assertThat(character.getInventory()).isEmpty();
        assertThat(warmedRoom.getItems()).containsExactly(torchWithTemplate);
    }

    @Test
    void warmRoomItemsAttachesTemplatesToItemsOnTheGround() {
        ItemTemplate template = new ItemTemplate(UUID.randomUUID(), "Bouclier", null, ItemType.ARMOR, 4, null, 0);
        itemService.registerTemplate(template);

        roomService.warmRooms();
        Room room = roomService.allRooms().iterator().next();

        Item shield = new Item(UUID.randomUUID(), template.getId(), room.getId(), null, null);
        itemDao.insert(shield);

        itemService.warmRoomItems(roomService.allRooms());

        assertThat(room(room.getId()).getItems()).extracting(Item::getName).containsExactly("Bouclier");
    }

    @Test
    void warmItemTemplatesLoadsTheRealCatalogFromJson() {
        itemService.warmItemTemplates();

        Item potion = itemService.attachTemplate(
                new Item(UUID.randomUUID(), UUID.fromString("019fa0a5-80bf-7e84-87bf-5cf699c00315"), null, null, null));
        Item sword = itemService.attachTemplate(
                new Item(UUID.randomUUID(), UUID.fromString("019fa0a5-80c0-7035-9c2d-113b09a275df"), null, null, null));
        Item helmet = itemService.attachTemplate(
                new Item(UUID.randomUUID(), UUID.fromString("019faec6-116d-723d-b04c-76d51a2a2cb7"), null, null, null));

        assertThat(potion.getName()).isEqualTo("Potion de soin");
        assertThat(potion.getType()).isEqualTo(ItemType.POTION);
        assertThat(sword.getName()).isEqualTo("Epée courte");
        assertThat(sword.getType()).isEqualTo(ItemType.WEAPON);
        assertThat(helmet.getName()).isEqualTo("Casque de fer");
        assertThat(helmet.getType()).isEqualTo(ItemType.HELMET);
    }

    private Room room(UUID roomId) {
        return roomService.allRooms().stream().filter(room -> room.getId().equals(roomId)).findFirst().orElseThrow();
    }
}
