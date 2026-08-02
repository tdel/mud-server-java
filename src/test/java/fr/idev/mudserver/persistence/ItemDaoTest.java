package fr.idev.mudserver.persistence;

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

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class ItemDaoTest extends AbstractIntegrationTest {

    @Autowired
    private ItemTemplateDao itemTemplateDao;

    @Autowired
    private RoomDao roomDao;

    @Autowired
    private AccountDao accountDao;

    @Autowired
    private CharacterDao characterDao;

    @Autowired
    private ItemDao itemDao;

    private ItemTemplate template;
    private Room room;
    private Character character;

    private void seedTemplateRoomAndCharacter() {
        template = new ItemTemplate(UUID.randomUUID(), "Dague", null, ItemType.WEAPON, 1);
        itemTemplateDao.insert(template);
        room = new Room(UUID.randomUUID(), "Salle A", "...", true);
        roomDao.insert(room);
        Account account = new Account(UUID.randomUUID(), "dave", "hashed-password", null);
        accountDao.insert(account);
        character = new Character(UUID.randomUUID(), account.getId(), "Dave le Nain", room.getId(), Race.DWARF, 12, 12,
                10, 10, 12, 10, 12, 10, 10, 10);
        characterDao.insert(character);
    }

    @Test
    void insertsAndFindsById() {
        seedTemplateRoomAndCharacter();
        Item item = new Item(UUID.randomUUID(), template.getId(), room.getId(), null, null);

        itemDao.insert(item);

        assertThat(itemDao.findById(item.getId())).contains(item);
        assertThat(itemDao.findByRoomId(room.getId())).containsExactly(item);
    }

    @Test
    void assignToCharacterClearsRoomAndSlot() {
        seedTemplateRoomAndCharacter();
        Item item = new Item(UUID.randomUUID(), template.getId(), room.getId(), null, null);
        itemDao.insert(item);

        itemDao.assignToCharacter(item.getId(), character.getId());

        Item updated = itemDao.findById(item.getId()).orElseThrow();
        assertThat(updated.getCharacterId()).isEqualTo(character.getId());
        assertThat(updated.getRoomId()).isNull();
        assertThat(itemDao.findByCharacterId(character.getId())).containsExactly(updated);
    }

    @Test
    void assignToRoomClearsCharacterAndSlot() {
        seedTemplateRoomAndCharacter();
        Item item = new Item(UUID.randomUUID(), template.getId(), null, character.getId(), null);
        itemDao.insert(item);

        itemDao.assignToRoom(item.getId(), room.getId());

        Item updated = itemDao.findById(item.getId()).orElseThrow();
        assertThat(updated.getRoomId()).isEqualTo(room.getId());
        assertThat(updated.getCharacterId()).isNull();
    }

    @Test
    void updatesSlotForEquipAndUnequip() {
        seedTemplateRoomAndCharacter();
        Item item = new Item(UUID.randomUUID(), template.getId(), null, character.getId(), null);
        itemDao.insert(item);

        itemDao.updateSlot(item.getId(), EquipmentSlot.WEAPON);
        assertThat(itemDao.findById(item.getId())).map(Item::getSlot).contains(EquipmentSlot.WEAPON);

        itemDao.updateSlot(item.getId(), null);
        assertThat(itemDao.findById(item.getId()).orElseThrow().getSlot()).isNull();
    }

    @Test
    void findByIdForUpdateReadsTheSameRow() {
        seedTemplateRoomAndCharacter();
        Item item = new Item(UUID.randomUUID(), template.getId(), room.getId(), null, null);
        itemDao.insert(item);

        assertThat(itemDao.findByIdForUpdate(item.getId())).contains(item);
    }
}
