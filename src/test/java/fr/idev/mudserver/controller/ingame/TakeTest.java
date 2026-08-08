package fr.idev.mudserver.controller.ingame;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import fr.idev.mudserver.AbstractIntegrationTest;
import fr.idev.mudserver.controller.RecordingConnection;
import fr.idev.mudserver.domain.Account;
import fr.idev.mudserver.domain.Item;
import fr.idev.mudserver.domain.Room;
import fr.idev.mudserver.domain.actor.CharacterClass;
import fr.idev.mudserver.domain.actor.Gender;
import fr.idev.mudserver.domain.actor.GamePlayer;
import fr.idev.mudserver.domain.actor.Race;
import fr.idev.mudserver.domain.actor.TestAttributes;
import fr.idev.mudserver.domain.actor.TestProficiencies;
import fr.idev.mudserver.game.GameWorld;
import fr.idev.mudserver.game.ItemService;
import fr.idev.mudserver.game.RoomService;
import fr.idev.mudserver.network.message.Usage;
import fr.idev.mudserver.network.message.ingame.ItemNotFound;
import fr.idev.mudserver.network.message.ingame.ItemTaken;
import fr.idev.mudserver.persistence.AccountDao;
import fr.idev.mudserver.persistence.CharacterDao;
import fr.idev.mudserver.persistence.ItemDao;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class TakeTest extends AbstractIntegrationTest {

    // Template réel de data/items.json (Potion de soin), même id que
    // ItemServiceTest#warmItemTemplatesLoadsTheRealCatalogFromJson.
    private static final UUID POTION_TEMPLATE_ID = UUID.fromString("019fa0a5-80bf-7e84-87bf-5cf699c00315");

    @Autowired
    private Take take;

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

        take.onReceive(connection, "");

        assertThat(connection.received).containsExactly(new Usage("take <name>"));
    }

    @Test
    void itemNotInRoomSendsItemNotFound() {
        RecordingConnection connection = enterGame();

        take.onReceive(connection, "nonexistent");

        assertThat(connection.received).containsExactly(new ItemNotFound("nonexistent"));
    }

    @Test
    void successfulTakeSendsItemTakenAndMovesTheItemFromRoomToInventory() {
        itemService.warmItemTemplates();
        RecordingConnection connection = enterGame();
        GamePlayer character = gameWorld.character(connection);
        Room room = character.getCurrentRoom();
        Item item = new Item(UUID.randomUUID(), POTION_TEMPLATE_ID, room.getId(), null, null);
        itemDao.insert(item);
        itemService.warmRoomItems(roomService.allRooms());
        Item itemInRoom = room.findOneByName("Potion de soin").orElseThrow();

        take.onReceive(connection, "Potion de soin");

        assertThat(connection.received).containsExactly(new ItemTaken("Potion de soin"));
        assertThat(character.getInventory().getItems()).contains(itemInRoom);
        assertThat(room.getItems()).doesNotContain(itemInRoom);
    }

    private RecordingConnection enterGame() {
        roomService.warmRooms();
        Room startingRoom = roomService.startingRoom().orElseThrow();
        Account account = new Account(UUID.randomUUID(), "preneur-" + UUID.randomUUID(), "hashed-password", null);
        accountDao.insert(account);
        RecordingConnection connection = new RecordingConnection();
        GamePlayer character = new GamePlayer(UUID.randomUUID(), account.getId(), "Preneur", startingRoom.getId(),
                Gender.MAN, Race.HUMAN, CharacterClass.FIGHTER, TestProficiencies.savingThrows(CharacterClass.FIGHTER),
                TestProficiencies.skills(CharacterClass.FIGHTER), 1, 10, 10, TestAttributes.of(10, 10, 10, 10, 10, 10),
                0, 0);
        characterDao.insert(character);
        gameWorld.enterWorld(connection, character);
        connection.received.clear();
        return connection;
    }
}
