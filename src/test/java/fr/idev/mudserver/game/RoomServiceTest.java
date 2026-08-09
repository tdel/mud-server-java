package fr.idev.mudserver.game;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import fr.idev.mudserver.AbstractIntegrationTest;
import fr.idev.mudserver.domain.Account;
import fr.idev.mudserver.domain.HexCoordinate;
import fr.idev.mudserver.domain.MonsterSpawn;
import fr.idev.mudserver.domain.Room;
import fr.idev.mudserver.domain.RoomPortal;
import fr.idev.mudserver.domain.actor.CharacterClass;
import fr.idev.mudserver.domain.actor.Gender;
import fr.idev.mudserver.domain.actor.GamePlayer;
import fr.idev.mudserver.domain.actor.Race;
import fr.idev.mudserver.domain.actor.TestAttributes;
import fr.idev.mudserver.domain.actor.TestProficiencies;
import fr.idev.mudserver.game.RoomService.CellDefinition;
import fr.idev.mudserver.game.RoomService.MonsterSpawnDefinition;
import fr.idev.mudserver.game.RoomService.PortalDefinition;
import fr.idev.mudserver.game.RoomService.RoomDefinition;
import fr.idev.mudserver.game.actor.ClassService;
import fr.idev.mudserver.persistence.AccountDao;
import fr.idev.mudserver.persistence.CharacterDao;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

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

    @Autowired
    private ClassService classService;

    @Test
    void warmRoomsLoadsTheRealCatalogFromJson() {
        roomService.warmRooms();

        Room villageSquare = room(VILLAGE_SQUARE_ID);
        assertThat(villageSquare.getName()).isEqualTo("Place du village");
        assertThat(villageSquare.isStartingRoom()).isTrue();
        assertThat(villageSquare.findPortalAt(new HexCoordinate(15, 0))).map(RoomPortal::targetRoom).map(Room::getId)
                .contains(FOREST_EDGE_ID);
        assertThat(roomService.startingRoom()).map(Room::getId).contains(VILLAGE_SQUARE_ID);
    }

    @Test
    void loadRoomsThrowsWhenMoreThanOneRoomIsMarkedAsStarting() {
        RoomService isolated = new RoomService(new ObjectMapper(), characterDao);
        List<RoomDefinition> definitions = List.of(
                new RoomDefinition(UUID.randomUUID(), "A", "...", true, 16, 8, new CellDefinition(8, 4), List.of(),
                        List.of()),
                new RoomDefinition(UUID.randomUUID(), "B", "...", true, 16, 8, new CellDefinition(8, 4), List.of(),
                        List.of()));

        assertThatThrownBy(() -> isolated.loadRooms(definitions)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void loadRoomsThrowsWhenAPortalTargetsAnUnknownRoom() {
        RoomService isolated = new RoomService(new ObjectMapper(), characterDao);
        List<RoomDefinition> definitions = List.of(new RoomDefinition(UUID.randomUUID(), "A", "...", null, 16, 8,
                new CellDefinition(8, 4), List.of(new PortalDefinition(new CellDefinition(15, 4), "E",
                        UUID.randomUUID(), new CellDefinition(0, 4))),
                List.of()));

        assertThatThrownBy(() -> isolated.loadRooms(definitions)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void loadRoomsThrowsWhenAPortalCellIsNotOnTheBorder() {
        RoomService isolated = new RoomService(new ObjectMapper(), characterDao);
        UUID sourceId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        List<RoomDefinition> definitions = List.of(
                new RoomDefinition(sourceId, "Source", "...", null, 16, 8, new CellDefinition(8, 4),
                        List.of(new PortalDefinition(new CellDefinition(8, 4), "E", targetId,
                                new CellDefinition(0, 4))),
                        List.of()),
                new RoomDefinition(targetId, "Target", "...", null, 16, 8, new CellDefinition(8, 4), List.of(),
                        List.of()));

        assertThatThrownBy(() -> isolated.loadRooms(definitions)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void loadRoomsThrowsWhenAPortalTargetCellIsOutOfBoundsInTheTargetRoom() {
        RoomService isolated = new RoomService(new ObjectMapper(), characterDao);
        UUID sourceId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        List<RoomDefinition> definitions = List.of(
                new RoomDefinition(sourceId, "Source", "...", null, 16, 8, new CellDefinition(8, 4),
                        List.of(new PortalDefinition(new CellDefinition(15, 4), "E", targetId,
                                new CellDefinition(99, 99))),
                        List.of()),
                new RoomDefinition(targetId, "Target", "...", null, 16, 8, new CellDefinition(8, 4), List.of(),
                        List.of()));

        assertThatThrownBy(() -> isolated.loadRooms(definitions)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void loadRoomsThrowsWhenTwoPortalsShareTheSameCell() {
        RoomService isolated = new RoomService(new ObjectMapper(), characterDao);
        UUID sourceId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        List<RoomDefinition> definitions = List.of(new RoomDefinition(sourceId, "Source", "...", null, 16, 8,
                new CellDefinition(8, 4),
                List.of(new PortalDefinition(new CellDefinition(15, 4), "E", targetId, new CellDefinition(0, 4)),
                        new PortalDefinition(new CellDefinition(15, 4), "E", targetId, new CellDefinition(0, 5))),
                List.of()),
                new RoomDefinition(targetId, "Target", "...", null, 16, 8, new CellDefinition(8, 4), List.of(),
                        List.of()));

        assertThatThrownBy(() -> isolated.loadRooms(definitions)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void loadRoomsResolvesPortalsToTheAttachedSourceAndTargetRooms() {
        RoomService isolated = new RoomService(new ObjectMapper(), characterDao);
        UUID sourceId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        List<RoomDefinition> definitions = List.of(
                new RoomDefinition(sourceId, "Source", "...", null, 16, 8, new CellDefinition(8, 4),
                        List.of(new PortalDefinition(new CellDefinition(15, 4), "E", targetId,
                                new CellDefinition(0, 4))),
                        List.of()),
                new RoomDefinition(targetId, "Target", "...", null, 16, 8, new CellDefinition(8, 4), List.of(),
                        List.of()));

        isolated.loadRooms(definitions);

        Room source = isolated.allRooms().stream().filter(room -> room.getId().equals(sourceId)).findFirst()
                .orElseThrow();
        assertThat(source.findPortalAt(new HexCoordinate(15, 4))).map(RoomPortal::sourceRoom).contains(source);
        assertThat(source.findPortalAt(new HexCoordinate(15, 4))).map(RoomPortal::targetRoom).map(Room::getId)
                .contains(targetId);
        assertThat(source.findPortalAt(new HexCoordinate(0, 0))).isEmpty();
    }

    @Test
    void loadRoomsResolvesMonsterSpawnsToTheAttachedRoom() {
        RoomService isolated = new RoomService(new ObjectMapper(), characterDao);
        UUID roomId = UUID.randomUUID();
        UUID spawnId = UUID.randomUUID();
        UUID templateId = UUID.randomUUID();
        List<RoomDefinition> definitions = List
                .of(new RoomDefinition(roomId, "Room", "...", null, 16, 8, new CellDefinition(8, 4), List.of(),
                        List.of(new MonsterSpawnDefinition(spawnId, templateId, new CellDefinition(4, 2)))));

        isolated.loadRooms(definitions);

        Room room = isolated.allRooms().stream().filter(r -> r.getId().equals(roomId)).findFirst().orElseThrow();
        assertThat(room.getMonsterSpawns()).extracting(MonsterSpawn::id, MonsterSpawn::templateId, MonsterSpawn::cell)
                .containsExactly(tuple(spawnId, templateId, new HexCoordinate(4, 2)));
    }

    @Test
    void moveCharacterJoinsTheNewRoomAndPersistsIt() {
        roomService.warmRooms();
        classService.warmClassDefinitions();
        Room origin = room(VILLAGE_SQUARE_ID);
        Room destination = room(FOREST_EDGE_ID);

        Account account = new Account(UUID.randomUUID(), "erin", "hashed-password", null);
        accountDao.insert(account);
        GamePlayer character = new GamePlayer(UUID.randomUUID(), account.getId(), "Erin", origin.getId(), Gender.WOMAN,
                Race.HUMAN, CharacterClass.FIGHTER, TestProficiencies.primaryAbility(CharacterClass.FIGHTER),
                TestProficiencies.savingThrows(CharacterClass.FIGHTER),
                TestProficiencies.skills(CharacterClass.FIGHTER),
                TestProficiencies.weaponProficiencies(CharacterClass.FIGHTER),
                TestProficiencies.armorProficiencies(CharacterClass.FIGHTER), 1, 10, 10,
                TestAttributes.of(10, 10, 10, 10, 10, 10), 0, 0);
        characterDao.insert(character);
        origin.join(character);

        character.moveToRoom(destination);

        assertThat(destination.characters()).extracting(GamePlayer::getId).contains(character.getId());
        assertThat(origin.characters()).extracting(GamePlayer::getId).doesNotContain(character.getId());
        assertThat(character.getPosition()).isEqualTo(destination.getSpawnCell());
        assertThat(characterDao.findById(character.getId())).map(GamePlayer::getCurrentRoomId)
                .contains(destination.getId());
    }

    @Test
    void spawnCharacterResolvesTheCurrentRoomFromCurrentRoomIdAndJoinsIt() {
        roomService.warmRooms();
        classService.warmClassDefinitions();
        Room room = room(VILLAGE_SQUARE_ID);

        Account account = new Account(UUID.randomUUID(), "finn", "hashed-password", null);
        accountDao.insert(account);
        GamePlayer character = new GamePlayer(UUID.randomUUID(), account.getId(), "Finn", room.getId(), Gender.MAN,
                Race.HUMAN, CharacterClass.FIGHTER, TestProficiencies.primaryAbility(CharacterClass.FIGHTER),
                TestProficiencies.savingThrows(CharacterClass.FIGHTER),
                TestProficiencies.skills(CharacterClass.FIGHTER),
                TestProficiencies.weaponProficiencies(CharacterClass.FIGHTER),
                TestProficiencies.armorProficiencies(CharacterClass.FIGHTER), 1, 10, 10,
                TestAttributes.of(10, 10, 10, 10, 10, 10), 0, 0);
        characterDao.insert(character);

        roomService.spawnCharacter(character);

        assertThat(character.getCurrentRoom()).isEqualTo(room);
        assertThat(character.getPosition()).isEqualTo(room.getSpawnCell());
        assertThat(character.getCurrentRoom().characters()).extracting(GamePlayer::getId).contains(character.getId());
    }

    private Room room(UUID roomId) {
        return roomService.allRooms().stream().filter(room -> room.getId().equals(roomId)).findFirst().orElseThrow();
    }
}
