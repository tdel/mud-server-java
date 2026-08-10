package fr.idev.mudserver.domain.actor;

import java.util.List;
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

    @Test
    void movingIntoAMonstersPresenceRadiusStartsCombat() {
        Room village = warmVillage();
        GamePlayer character = seedCharacter(village, 100);
        GameMonster wolf = seedMonsterWithPresence(village, new HexCoordinate(12, 4), 2);

        MovementOutcome outcome = character.moveToCell(HexDirection.E, 6);

        assertThat(outcome.triggeredCombat()).isTrue();
        assertThat(outcome.cellsMoved()).isEqualTo(2);
        assertThat(character.getPosition()).isEqualTo(new HexCoordinate(10, 4));
        assertThat(character.isInCombat()).isTrue();
        assertThat(wolf.isInCombat()).isTrue();
        assertThat(character.getEncounter()).isSameAs(wolf.getEncounter());
    }

    @Test
    void movingOutsideAnyMonstersRadiusDoesNotStartCombat() {
        Room village = warmVillage();
        GamePlayer character = seedCharacter(village, 100);
        GameMonster wolf = seedMonsterWithPresence(village, new HexCoordinate(12, 4), 1);

        MovementOutcome outcome = character.moveToCell(HexDirection.E, 1);

        assertThat(outcome.triggeredCombat()).isFalse();
        assertThat(character.isInCombat()).isFalse();
        assertThat(wolf.isInCombat()).isFalse();
    }

    @Test
    void remainingInAMonstersRadiusAcrossMultipleMovesDoesNotRetriggerAmbush() {
        Room village = warmVillage();
        GamePlayer character = seedCharacter(village, 100);
        GameMonster wolf = seedMonsterWithPresence(village, new HexCoordinate(12, 4), 2);

        character.moveToCell(HexDirection.E, 6);
        CombatEncounter encounterAfterFirstMove = character.getEncounter();
        assertThat(encounterAfterFirstMove).isNotNull();

        MovementOutcome secondOutcome = character.moveToCell(HexDirection.E, 1);

        assertThat(secondOutcome.triggeredCombat()).isFalse();
        assertThat(character.getEncounter()).isSameAs(encounterAfterFirstMove);
        assertThat(wolf.getEncounter()).isSameAs(encounterAfterFirstMove);
    }

    @Test
    void secondMonsterInRangeSimultaneouslyJoinsTheSameEncounter() {
        Room village = warmVillage();
        GamePlayer character = seedCharacter(village, 100);
        GameMonster wolf = seedMonsterWithPresence(village, new HexCoordinate(11, 4), 1);
        GameMonster spider = seedMonsterWithPresence(village, new HexCoordinate(12, 4), 2);

        MovementOutcome outcome = character.moveToCell(HexDirection.E, 6);

        assertThat(outcome.triggeredCombat()).isTrue();
        assertThat(character.getPosition()).isEqualTo(new HexCoordinate(10, 4));
        assertThat(character.isInCombat()).isTrue();
        assertThat(wolf.isInCombat()).isTrue();
        assertThat(spider.isInCombat()).isTrue();
        CombatEncounter encounter = character.getEncounter();
        assertThat(wolf.getEncounter()).isSameAs(encounter);
        assertThat(spider.getEncounter()).isSameAs(encounter);
    }

    @Test
    void rollInitiativeIsWithinTheExpectedRangeForTheDexterityModifier() {
        GamePlayer character = characterWithDexterity(18); // DEX 18 => modificateur +4

        for (int i = 0; i < 50; i++) {
            assertThat(character.rollInitiative()).isBetween(1 + 4, 20 + 4);
        }
    }

    private GamePlayer characterWithDexterity(int dexterity) {
        return new GamePlayer(UUID.randomUUID(), UUID.randomUUID(), "Test", UUID.randomUUID(), Gender.MAN, Race.HUMAN,
                CharacterClass.FIGHTER, 1, 10, 10, TestAttributes.of(10, dexterity, 10, 10, 10, 10), 0, 0);
    }

    private Room warmVillage() {
        roomService.warmRooms();
        return roomService.allRooms().stream().filter(room -> room.getId().equals(VILLAGE_SQUARE_ID)).findFirst()
                .orElseThrow();
    }

    private GamePlayer seedCharacter(Room room) {
        return seedCharacter(room, 10);
    }

    private GamePlayer seedCharacter(Room room, int hp) {
        Account account = new Account(UUID.randomUUID(), "movement-" + UUID.randomUUID(), "hashed-password", null);
        accountDao.insert(account);
        GamePlayer character = new GamePlayer(UUID.randomUUID(), account.getId(), "Mover", room.getId(), Gender.MAN,
                Race.HUMAN, CharacterClass.FIGHTER, 1, hp, hp, TestAttributes.of(10, 10, 10, 10, 10, 10), 0, 0);
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

    /**
     * Contrairement à {@link #seedMonster}, attache un {@link MonsterTemplate} réel
     * avec un {@code presenceRadius} configuré : nécessaire pour tout test d'aggro,
     * {@link GameMonster#getPresenceRadius()} délégant au template et levant sinon
     * une {@code IllegalStateException}. Dégâts naturels bornés à {@code 1d2} pour
     * que le joueur (100 PV dans ces tests) survive à coup sûr au coup d'ouverture
     * si le monstre gagne l'initiative.
     */
    private GameMonster seedMonsterWithPresence(Room room, HexCoordinate cell, int presenceRadius) {
        MonsterTemplate template = new MonsterTemplate(UUID.randomUUID(), "Loup", "Un loup gris.", 10,
                TestAttributes.of(10, 10, 10, 10, 10, 10), 10, 0, "1d2", 0, List.of(), presenceRadius);
        GameMonster monster = new GameMonster(UUID.randomUUID(), template.getName(), template.getId(), room.getId(),
                template.getAttributes(), template.getMaxHealth());
        monster.attachTemplate(template);
        monster.setCurrentRoom(room);
        room.placeMonster(monster, cell);
        return monster;
    }
}
