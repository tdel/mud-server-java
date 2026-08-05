package fr.idev.mudserver.persistence;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import fr.idev.mudserver.AbstractIntegrationTest;
import fr.idev.mudserver.domain.Account;
import fr.idev.mudserver.domain.actor.GamePlayer;
import fr.idev.mudserver.domain.actor.CharacterClass;
import fr.idev.mudserver.domain.EquipmentSlot;
import fr.idev.mudserver.domain.actor.Gender;
import fr.idev.mudserver.domain.Item;
import fr.idev.mudserver.domain.actor.Race;
import fr.idev.mudserver.domain.actor.TestAttributes;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class ItemDaoTest extends AbstractIntegrationTest {

    @Autowired
    private AccountDao accountDao;

    @Autowired
    private CharacterDao characterDao;

    @Autowired
    private ItemDao itemDao;

    private UUID templateId;
    private UUID roomId;
    private GamePlayer character;

    private void seedTemplateRoomAndCharacter() {
        templateId = UUID.randomUUID();
        roomId = UUID.randomUUID();
        Account account = new Account(UUID.randomUUID(), "dave", "hashed-password", null);
        accountDao.insert(account);
        character = new GamePlayer(UUID.randomUUID(), account.getId(), "Dave le Nain", roomId, Gender.MAN, Race.DWARF,
                CharacterClass.FIGHTER, 1, 12, 12, TestAttributes.of(12, 10, 12, 10, 10, 10), 0);
        characterDao.insert(character);
    }

    @Test
    void insertsAndFindsById() {
        seedTemplateRoomAndCharacter();
        Item item = new Item(UUID.randomUUID(), templateId, roomId, null, null);

        itemDao.insert(item);

        assertThat(itemDao.findById(item.getId())).contains(item);
        assertThat(itemDao.findByRoomId(roomId)).containsExactly(item);
    }

    @Test
    void assignToCharacterClearsRoomAndSlot() {
        seedTemplateRoomAndCharacter();
        Item item = new Item(UUID.randomUUID(), templateId, roomId, null, null);
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
        Item item = new Item(UUID.randomUUID(), templateId, null, character.getId(), null);
        itemDao.insert(item);

        itemDao.assignToRoom(item.getId(), roomId);

        Item updated = itemDao.findById(item.getId()).orElseThrow();
        assertThat(updated.getRoomId()).isEqualTo(roomId);
        assertThat(updated.getCharacterId()).isNull();
    }

    @Test
    void updatesSlotForEquipAndUnequip() {
        seedTemplateRoomAndCharacter();
        Item item = new Item(UUID.randomUUID(), templateId, null, character.getId(), null);
        itemDao.insert(item);

        itemDao.updateSlot(item.getId(), EquipmentSlot.WEAPON);
        assertThat(itemDao.findById(item.getId())).map(Item::getSlot).contains(EquipmentSlot.WEAPON);

        itemDao.updateSlot(item.getId(), null);
        assertThat(itemDao.findById(item.getId()).orElseThrow().getSlot()).isNull();
    }
}
