package fr.idev.mudserver.domain.actor;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import fr.idev.mudserver.AbstractIntegrationTest;
import fr.idev.mudserver.domain.Account;
import fr.idev.mudserver.domain.HexCoordinate;
import fr.idev.mudserver.domain.HexDirection;
import fr.idev.mudserver.domain.Room;
import fr.idev.mudserver.domain.actor.GameCharacter.MovementOutcome;
import fr.idev.mudserver.game.RoomService;
import fr.idev.mudserver.game.actor.ClassService;
import fr.idev.mudserver.persistence.AccountDao;
import fr.idev.mudserver.persistence.CharacterDao;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class GameCharacterTest extends AbstractIntegrationTest {

    private static final UUID VILLAGE_SQUARE_ID = UUID.fromString("5e4ada37-37e1-438c-9233-581f10c055c7");
    private static final UUID TAVERN_ID = UUID.fromString("e1da77bd-f0b3-4d5a-95da-e8765c4fc973");

    @Autowired
    private RoomService roomService;

    @Autowired
    private AccountDao accountDao;

    @Autowired
    private CharacterDao characterDao;

    @Autowired
    private ClassService classService;

    @Test
    void singleStepMoveUpdatesPosition() {
        Room village = warmVillage();
        GamePlayer character = seedCharacter(village);

        MovementOutcome outcome = character.moveToCell(HexDirection.E, 1);

        assertThat(outcome.cellsMoved()).isEqualTo(1);
        assertThat(character.getPosition()).isEqualTo(new HexCoordinate(9, 4));
    }

    @Test
    void requestedCellsAreCappedBySpeed() {
        Room village = warmVillage();
        GamePlayer character = seedCharacter(village);

        MovementOutcome outcome = character.moveToCell(HexDirection.E, 99);

        assertThat(character.getSpeed()).isEqualTo(6);
        assertThat(outcome.cellsMoved()).isEqualTo(6);
        assertThat(character.getPosition()).isEqualTo(new HexCoordinate(14, 4));
    }

    @Test
    void movementIsBlockedAtTheGridEdge() {
        Room village = warmVillage();
        GamePlayer character = seedCharacter(village);
        village.leave(character);
        village.join(character, new HexCoordinate(1, 0));

        MovementOutcome outcome = character.moveToCell(HexDirection.NW, 1);

        assertThat(outcome.cellsMoved()).isZero();
        assertThat(outcome.blockedByBounds()).isTrue();
        assertThat(character.getPosition()).isEqualTo(new HexCoordinate(1, 0));
    }

    @Test
    void landingOnAPortalCrossesToTheLinkedRoomAndPersistsCurrentRoomId() {
        Room village = warmVillage();
        Room tavern = roomService.allRooms().stream().filter(room -> room.getId().equals(TAVERN_ID)).findFirst()
                .orElseThrow();
        GamePlayer character = seedCharacter(village);
        village.leave(character);
        village.join(character, new HexCoordinate(14, 4));

        MovementOutcome outcome = character.moveToCell(HexDirection.E, 1);

        assertThat(outcome.crossedPortal()).isTrue();
        assertThat(character.getCurrentRoom()).isEqualTo(tavern);
        assertThat(character.getPosition()).isEqualTo(new HexCoordinate(0, 4));
        assertThat(characterDao.findById(character.getId())).map(GamePlayer::getCurrentRoomId).contains(TAVERN_ID);
    }

    @Test
    void aMonsterReachingAPortalCellStopsThereWithoutCrossingToTheLinkedRoom() {
        Room village = warmVillage();
        GameMonster monster = seedMonster(village, new HexCoordinate(14, 4));

        MovementOutcome outcome = monster.moveToCell(HexDirection.E, 2);

        assertThat(outcome.cellsMoved()).isEqualTo(1);
        assertThat(outcome.crossedPortal()).isFalse();
        assertThat(monster.getPosition()).isEqualTo(new HexCoordinate(15, 4));
        assertThat(monster.getCurrentRoom()).isEqualTo(village);
    }

    private Room warmVillage() {
        roomService.warmRooms();
        classService.warmClassDefinitions();
        return roomService.allRooms().stream().filter(room -> room.getId().equals(VILLAGE_SQUARE_ID)).findFirst()
                .orElseThrow();
    }

    private GamePlayer seedCharacter(Room room) {
        Account account = new Account(UUID.randomUUID(), "movement-" + UUID.randomUUID(), "hashed-password", null);
        accountDao.insert(account);
        GamePlayer character = new GamePlayer(UUID.randomUUID(), account.getId(), "Mover", room.getId(), Gender.MAN,
                Race.HUMAN, CharacterClass.FIGHTER, TestProficiencies.savingThrows(CharacterClass.FIGHTER),
                TestProficiencies.skills(CharacterClass.FIGHTER), 1, 10, 10, TestAttributes.of(10, 10, 10, 10, 10, 10),
                0, 0);
        characterDao.insert(character);
        room.join(character);
        return character;
    }

    private GameMonster seedMonster(Room room, HexCoordinate cell) {
        GameMonster monster = new GameMonster(UUID.randomUUID(), "Loup", UUID.randomUUID(), room.getId(),
                TestAttributes.of(10, 10, 10, 10, 10, 10), 10);
        monster.setCurrentRoom(room);
        room.placeMonster(monster, cell);
        return monster;
    }
}
