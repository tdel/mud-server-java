package fr.idev.mudserver.controller.ingame;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import fr.idev.mudserver.AbstractIntegrationTest;
import fr.idev.mudserver.controller.RecordingConnection;
import fr.idev.mudserver.domain.Account;
import fr.idev.mudserver.domain.EquipmentSlot;
import fr.idev.mudserver.domain.Item;
import fr.idev.mudserver.domain.Rarity;
import fr.idev.mudserver.domain.RoomInstance;
import fr.idev.mudserver.domain.actor.CharacterClass;
import fr.idev.mudserver.domain.actor.Gender;
import fr.idev.mudserver.domain.actor.GamePlayer;
import fr.idev.mudserver.domain.actor.Race;
import fr.idev.mudserver.domain.actor.TestAttributes;
import fr.idev.mudserver.game.GameWorld;
import fr.idev.mudserver.game.ItemService;
import fr.idev.mudserver.game.RoomService;
import fr.idev.mudserver.network.message.Usage;
import fr.idev.mudserver.network.message.ingame.ItemEquipped;
import fr.idev.mudserver.network.message.ingame.ItemNotCarried;
import fr.idev.mudserver.network.message.ingame.ItemNotEquippable;
import fr.idev.mudserver.persistence.AccountDao;
import fr.idev.mudserver.persistence.CharacterDao;
import fr.idev.mudserver.persistence.ItemDao;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class EquipTest extends AbstractIntegrationTest {

    // Templates réels de data/items.json — Epée courte (WEAPON, équipable) et
    // Potion de soin (POTION, sans EquipmentSlot associé, cf.
    // ItemType#equipmentSlot).
    private static final UUID SWORD_TEMPLATE_ID = UUID.fromString("019fa0a5-80c0-7035-9c2d-113b09a275df");
    private static final UUID POTION_TEMPLATE_ID = UUID.fromString("019fa0a5-80bf-7e84-87bf-5cf699c00315");

    @Autowired
    private Equip equip;

    @Autowired
    private GameWorld gameWorld;

    @Autowired
    private RoomService roomService;

    @Autowired
    private ItemService itemService;

    @Autowired
    private ItemDao itemDao;

    @Autowired
    private AccountDao accountDao;

    @Autowired
    private CharacterDao characterDao;

    @Test
    void emptyArgumentSendsUsage() {
        RecordingConnection connection = enterGame();

        equip.onReceive(connection, "");

        assertThat(connection.received).containsExactly(new Usage("equip <name>"));
    }

    @Test
    void itemNotCarriedSendsItemNotCarried() {
        RecordingConnection connection = enterGame();

        equip.onReceive(connection, "Epée courte");

        assertThat(connection.received).containsExactly(new ItemNotCarried("Epée courte"));
    }

    @Test
    void itemNotEquippableSendsItemNotEquippable() {
        RecordingConnection connection = enterGame();
        GamePlayer character = gameWorld.character(connection);
        addToInventory(character, POTION_TEMPLATE_ID);

        equip.onReceive(connection, "Potion de soin");

        assertThat(connection.received).containsExactly(new ItemNotEquippable("Potion de soin"));
    }

    @Test
    void successfulEquipSendsItemEquippedWithTheResolvedSlot() {
        RecordingConnection connection = enterGame();
        GamePlayer character = gameWorld.character(connection);
        addToInventory(character, SWORD_TEMPLATE_ID);

        equip.onReceive(connection, "Epée courte");

        assertThat(connection.received)
                .containsExactly(new ItemEquipped("Epée courte", Rarity.COMMON, EquipmentSlot.WEAPON));
    }

    private void addToInventory(GamePlayer character, UUID templateId) {
        itemService.warmItemTemplates();
        Item item = new Item(UUID.randomUUID(), templateId, null, character.getId(), null);
        itemDao.insert(item);
        character.getInventory().replaceItems(itemService.loadInventory(character));
    }

    private RecordingConnection enterGame() {
        roomService.warmRooms();
        RoomInstance startingRoom = roomService.startingRoom().orElseThrow();
        Account account = new Account(UUID.randomUUID(), "equipeur-" + UUID.randomUUID(), "hashed-password", null);
        accountDao.insert(account);
        RecordingConnection connection = new RecordingConnection();
        GamePlayer character = new GamePlayer(UUID.randomUUID(), account.getId(), "Equipeur", startingRoom.getId(),
                Gender.MAN, Race.HUMAN, CharacterClass.FIGHTER, 1, 10, 10, TestAttributes.of(10, 10, 10, 10, 10, 10), 0,
                0);
        characterDao.insert(character);
        gameWorld.enterWorld(connection, character);
        connection.received.clear();
        return connection;
    }
}
