package fr.idev.mudserver.game;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import fr.idev.mudserver.AbstractIntegrationTest;
import fr.idev.mudserver.domain.Account;
import fr.idev.mudserver.domain.HexCoordinate;
import fr.idev.mudserver.domain.RoomInstance;
import fr.idev.mudserver.domain.RoomPortal;
import fr.idev.mudserver.domain.WorldInstance;
import fr.idev.mudserver.domain.actor.Attribute;
import fr.idev.mudserver.domain.actor.CharacterClass;
import fr.idev.mudserver.domain.actor.Gender;
import fr.idev.mudserver.domain.actor.GameMonster;
import fr.idev.mudserver.domain.actor.GamePlayer;
import fr.idev.mudserver.domain.actor.Race;
import fr.idev.mudserver.domain.actor.TestAttributes;
import fr.idev.mudserver.persistence.AccountDao;
import fr.idev.mudserver.persistence.CharacterDao;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class RoomServiceTest extends AbstractIntegrationTest {

    private static final UUID VILLAGE_SQUARE_ID = UUID.fromString("5e4ada37-37e1-438c-9233-581f10c055c7");
    private static final UUID FOREST_EDGE_ID = UUID.fromString("9a884ac7-b954-4cd6-ab67-c677d472cb0f");
    private static final UUID TAVERN_ID = UUID.fromString("e1da77bd-f0b3-4d5a-95da-e8765c4fc973");
    private static final UUID CLEARING_ID = UUID.fromString("7f55fd0c-23f8-4a3b-82a2-95a79bdbf2b5");
    private static final UUID CEMETERY_PATH_ID = UUID.fromString("4dae9974-45f7-46c9-8e66-12cdac759860");

    @Autowired
    private RoomService roomService;

    @Autowired
    private AccountDao accountDao;

    @Autowired
    private CharacterDao characterDao;

    @Autowired
    private WorldInstanceService worldInstanceService;

    @Test
    void warmRoomsLoadsTheRealCatalogFromJson() {
        roomService.warmRooms();

        RoomInstance villageSquare = room(VILLAGE_SQUARE_ID);
        assertThat(villageSquare.getName()).isEqualTo("Place du village");
        assertThat(villageSquare.isStartingRoom()).isTrue();
        assertThat(villageSquare.findPortalAt(new HexCoordinate(15, 0))).map(RoomPortal::targetRoom)
                .map(RoomInstance::getTemplateId).contains(FOREST_EDGE_ID);
        assertThat(roomService.startingRoom()).map(RoomInstance::getTemplateId).contains(VILLAGE_SQUARE_ID);
    }

    @Test
    void warmRoomsResolvesPortalsToTheAttachedSourceAndTargetRoomObjects() {
        roomService.warmRooms();

        RoomInstance villageSquare = room(VILLAGE_SQUARE_ID);
        RoomPortal portal = villageSquare.findPortalAt(new HexCoordinate(15, 0)).orElseThrow();
        assertThat(portal.sourceRoom()).isEqualTo(villageSquare);
        assertThat(portal.targetRoom()).isEqualTo(room(FOREST_EDGE_ID));
    }

    @Test
    void moveCharacterJoinsTheNewRoomAndPersistsIt() {
        roomService.warmRooms();
        RoomInstance origin = room(VILLAGE_SQUARE_ID);
        RoomInstance destination = room(FOREST_EDGE_ID);

        Account account = new Account(UUID.randomUUID(), "erin", "hashed-password", null);
        accountDao.insert(account);
        GamePlayer character = new GamePlayer(UUID.randomUUID(), account, "Erin", origin, Gender.WOMAN, Race.HUMAN,
                CharacterClass.FIGHTER, 1, 10, 10, TestAttributes.of(10, 10, 10, 10, 10, 10), 0, 0);
        characterDao.insert(character);
        origin.join(character);

        character.moveToRoom(destination);

        assertThat(destination.characters()).extracting(GamePlayer::getId).contains(character.getId());
        assertThat(origin.characters()).extracting(GamePlayer::getId).doesNotContain(character.getId());
        assertThat(character.getPosition()).isEqualTo(destination.getSpawnCell());
        WorldInstance instance = worldInstanceService.getOrMaterialize(WorldInstance.DEFAULT_ID);
        assertThat(characterDao.findByAccountAndWorldInstance(account, instance)).map(GamePlayer::getCurrentRoomId)
                .contains(destination.getTemplateId());
    }

    @Test
    void spawnCharacterResolvesTheCurrentRoomFromCurrentRoomIdAndJoinsIt() {
        roomService.warmRooms();
        RoomInstance room = room(VILLAGE_SQUARE_ID);

        Account account = new Account(UUID.randomUUID(), "finn", "hashed-password", null);
        accountDao.insert(account);
        GamePlayer character = new GamePlayer(UUID.randomUUID(), account, "Finn", room, Gender.MAN, Race.HUMAN,
                CharacterClass.FIGHTER, 1, 10, 10, TestAttributes.of(10, 10, 10, 10, 10, 10), 0, 0);
        characterDao.insert(character);

        roomService.spawnCharacter(character);

        assertThat(character.getCurrentRoom()).isEqualTo(room);
        assertThat(character.getPosition()).isEqualTo(room.getSpawnCell());
        assertThat(character.getCurrentRoom().characters()).extracting(GamePlayer::getId).contains(character.getId());
    }

    /**
     * {@code warmRooms()} matérialise désormais l'instance par défaut avec son
     * contenu runtime complet — voir {@code WorldInstanceService.materialize} —
     * donc les monstres réels du catalogue sont déjà placés à la sortie de ce seul
     * appel, sans étape séparée.
     */
    @Test
    void warmRoomsPlacesRealMonstersFromJsonInTheirRooms() {
        roomService.warmRooms();

        RoomInstance villageSquare = room(VILLAGE_SQUARE_ID);
        RoomInstance forestEdge = room(FOREST_EDGE_ID);
        RoomInstance tavern = room(TAVERN_ID);
        RoomInstance clearing = room(CLEARING_ID);
        RoomInstance cemeteryPath = room(CEMETERY_PATH_ID);

        assertThat(villageSquare.getMonsters()).hasSize(0);
        assertThat(forestEdge.getMonsters()).hasSize(2);
        assertThat(tavern.getMonsters()).hasSize(1);
        assertThat(clearing.getMonsters()).hasSize(3);
        assertThat(cemeteryPath.getMonsters()).hasSize(1);
        GameMonster goblin = clearing.getMonsters().get(0);
        assertThat(goblin.getName()).isEqualTo("Gobelin");
        assertThat(goblin.getMaxHealth()).isEqualTo(7);
        assertThat(goblin.getCurrentHealth()).isEqualTo(7);
        assertThat(goblin.getAttribute(Attribute.DEXTERITY)).isEqualTo(14);
        assertThat(goblin.getArmorClass()).isEqualTo(15);
        assertThat(goblin.getCurrentRoom()).isEqualTo(clearing);
        assertThat(goblin.getDescription()).isNotBlank();
        assertThat(goblin.getTemplate().getXpReward()).isEqualTo(50);
        assertThat(goblin.getTemplate().getGoldReward()).isEqualTo(5);
        assertThat(goblin.getTemplate().getLootTable()).isNotEmpty();
        assertThat(goblin.getPresenceRadius()).isEqualTo(2);
    }

    private RoomInstance room(UUID roomTemplateId) {
        return roomService.allRooms().stream().filter(room -> room.getTemplateId().equals(roomTemplateId)).findFirst()
                .orElseThrow();
    }
}
