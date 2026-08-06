package fr.idev.mudserver.game;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import fr.idev.mudserver.AbstractIntegrationTest;
import fr.idev.mudserver.domain.Account;
import fr.idev.mudserver.domain.actor.GamePlayer;
import fr.idev.mudserver.domain.actor.CharacterClass;
import fr.idev.mudserver.domain.actor.Gender;
import fr.idev.mudserver.domain.actor.Race;
import fr.idev.mudserver.domain.Room;
import fr.idev.mudserver.domain.RoomExit;
import fr.idev.mudserver.domain.actor.TestAttributes;
import fr.idev.mudserver.persistence.AccountDao;
import fr.idev.mudserver.persistence.CharacterDao;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Transactional
class RoomServiceTest extends AbstractIntegrationTest {

    private static final UUID VILLAGE_SQUARE_ID = UUID.fromString("5e4ada37-37e1-438c-9233-581f10c055c7");
    private static final UUID FOREST_EDGE_ID = UUID.fromString("9a884ac7-b954-4cd6-ab67-c677d472cb0f");

    @Autowired
    private RoomService roomService;

    @Autowired
    private AccountDao accountDao;

    @Autowired
    private CharacterDao characterDao;

    @Test
    void warmRoomsLoadsTheRealCatalogFromJson() {
        roomService.warmRooms();

        Room villageSquare = room(VILLAGE_SQUARE_ID);
        assertThat(villageSquare.getName()).isEqualTo("Place du village");
        assertThat(villageSquare.isStartingRoom()).isTrue();
        assertThat(villageSquare.findOneByDirection("nord")).map(RoomExit::getTargetRoom).map(Room::getId)
                .contains(FOREST_EDGE_ID);
        assertThat(roomService.startingRoom()).map(Room::getId).contains(VILLAGE_SQUARE_ID);
    }

    @Test
    void loadRoomsThrowsWhenMoreThanOneRoomIsMarkedAsStarting() {
        RoomService isolated = new RoomService(new ObjectMapper(), characterDao);
        List<RoomService.RoomDefinition> definitions = List.of(
                new RoomService.RoomDefinition(UUID.randomUUID(), "A", "...", true, List.of()),
                new RoomService.RoomDefinition(UUID.randomUUID(), "B", "...", true, List.of()));

        assertThatThrownBy(() -> isolated.loadRooms(definitions)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void loadRoomsThrowsWhenAnExitTargetsAnUnknownRoom() {
        RoomService isolated = new RoomService(new ObjectMapper(), characterDao);
        List<RoomService.RoomDefinition> definitions = List.of(new RoomService.RoomDefinition(UUID.randomUUID(), "A",
                "...", null, List.of(new RoomService.ExitDefinition("nord", UUID.randomUUID()))));

        assertThatThrownBy(() -> isolated.loadRooms(definitions)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void loadRoomsResolvesExitsToTheAttachedSourceAndTargetRooms() {
        RoomService isolated = new RoomService(new ObjectMapper(), characterDao);
        UUID sourceId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        List<RoomService.RoomDefinition> definitions = List.of(
                new RoomService.RoomDefinition(sourceId, "Source", "...", null,
                        List.of(new RoomService.ExitDefinition("nord", targetId))),
                new RoomService.RoomDefinition(targetId, "Target", "...", null, List.of()));

        isolated.loadRooms(definitions);

        Room source = isolated.allRooms().stream().filter(room -> room.getId().equals(sourceId)).findFirst()
                .orElseThrow();
        assertThat(source.findOneByDirection("nord")).map(RoomExit::getSourceRoom).contains(source);
        assertThat(source.findOneByDirection("nord")).map(RoomExit::getTargetRoom).map(Room::getId).contains(targetId);
        assertThat(source.findOneByDirection("sud")).isEmpty();
    }

    @Test
    void moveCharacterJoinsTheNewRoomAndPersistsIt() {
        roomService.warmRooms();
        Room origin = room(VILLAGE_SQUARE_ID);
        Room destination = room(FOREST_EDGE_ID);

        Account account = new Account(UUID.randomUUID(), "erin", "hashed-password", null);
        accountDao.insert(account);
        GamePlayer character = new GamePlayer(UUID.randomUUID(), account.getId(), "Erin", origin.getId(), Gender.WOMAN,
                Race.HUMAN, CharacterClass.FIGHTER, 1, 10, 10, TestAttributes.of(10, 10, 10, 10, 10, 10), 0, 0);
        characterDao.insert(character);
        origin.join(character);

        character.moveToRoom(destination);

        assertThat(destination.characters()).extracting(GamePlayer::getId).contains(character.getId());
        assertThat(origin.characters()).extracting(GamePlayer::getId).doesNotContain(character.getId());
        assertThat(characterDao.findById(character.getId())).map(GamePlayer::getCurrentRoomId)
                .contains(destination.getId());
    }

    @Test
    void spawnCharacterResolvesTheCurrentRoomFromCurrentRoomIdAndJoinsIt() {
        roomService.warmRooms();
        Room room = room(VILLAGE_SQUARE_ID);

        Account account = new Account(UUID.randomUUID(), "finn", "hashed-password", null);
        accountDao.insert(account);
        GamePlayer character = new GamePlayer(UUID.randomUUID(), account.getId(), "Finn", room.getId(), Gender.MAN,
                Race.HUMAN, CharacterClass.FIGHTER, 1, 10, 10, TestAttributes.of(10, 10, 10, 10, 10, 10), 0, 0);
        characterDao.insert(character);

        roomService.spawnCharacter(character);

        assertThat(character.getCurrentRoom()).isEqualTo(room);
        assertThat(character.getCurrentRoom().characters()).extracting(GamePlayer::getId).contains(character.getId());
    }

    private Room room(UUID roomId) {
        return roomService.allRooms().stream().filter(room -> room.getId().equals(roomId)).findFirst().orElseThrow();
    }
}
