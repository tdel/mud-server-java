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
        character = new Character(UUID.randomUUID(), account.id(), "Dave le Nain", room.id(), Race.DWARF, 12, 12, 10,
                10, 12, 10, 12, 10, 10, 10);
        characterDao.insert(character);
    }

    @Test
    void insertsAndFindsById() {
        seedTemplateRoomAndCharacter();
        Item item = new Item(UUID.randomUUID(), template.id(), room.id(), null, null);

        itemDao.insert(item);

        assertThat(itemDao.findById(item.id())).contains(item);
        assertThat(itemDao.findByRoomId(room.id())).containsExactly(item);
    }

    @Test
    void assignToCharacterClearsRoomAndSlot() {
        seedTemplateRoomAndCharacter();
        Item item = new Item(UUID.randomUUID(), template.id(), room.id(), null, null);
        itemDao.insert(item);

        itemDao.assignToCharacter(item.id(), character.id());

        Item updated = itemDao.findById(item.id()).orElseThrow();
        assertThat(updated.characterId()).isEqualTo(character.id());
        assertThat(updated.roomId()).isNull();
        assertThat(itemDao.findByCharacterId(character.id())).containsExactly(updated);
    }

    @Test
    void assignToRoomClearsCharacterAndSlot() {
        seedTemplateRoomAndCharacter();
        Item item = new Item(UUID.randomUUID(), template.id(), null, character.id(), null);
        itemDao.insert(item);

        itemDao.assignToRoom(item.id(), room.id());

        Item updated = itemDao.findById(item.id()).orElseThrow();
        assertThat(updated.roomId()).isEqualTo(room.id());
        assertThat(updated.characterId()).isNull();
    }

    @Test
    void updatesSlotForEquipAndUnequip() {
        seedTemplateRoomAndCharacter();
        Item item = new Item(UUID.randomUUID(), template.id(), null, character.id(), null);
        itemDao.insert(item);

        itemDao.updateSlot(item.id(), EquipmentSlot.WEAPON);
        assertThat(itemDao.findById(item.id())).map(Item::slot).contains(EquipmentSlot.WEAPON);

        itemDao.updateSlot(item.id(), null);
        assertThat(itemDao.findById(item.id()).orElseThrow().slot()).isNull();
    }

    @Test
    void findByIdForUpdateReadsTheSameRow() {
        seedTemplateRoomAndCharacter();
        Item item = new Item(UUID.randomUUID(), template.id(), room.id(), null, null);
        itemDao.insert(item);

        assertThat(itemDao.findByIdForUpdate(item.id())).contains(item);
    }
}
