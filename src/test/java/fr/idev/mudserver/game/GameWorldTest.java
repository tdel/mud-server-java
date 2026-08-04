package fr.idev.mudserver.game;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.annotation.Transactional;

import fr.idev.mudserver.AbstractIntegrationTest;
import fr.idev.mudserver.domain.Account;
import fr.idev.mudserver.domain.Attribute;
import fr.idev.mudserver.domain.Character;
import fr.idev.mudserver.domain.Race;
import fr.idev.mudserver.domain.Room;
import fr.idev.mudserver.persistence.AccountDao;
import fr.idev.mudserver.persistence.CharacterDao;
import fr.idev.mudserver.persistence.RoomDao;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Non-régression : GameWorld#createCharacter publie NewCharacterCreated, dont
 * le listener onNewCharacterCreated (dans cette même classe) fait le
 * characterDao.insert — ce test prouve que la persistance a bien lieu malgré
 * l'indirection par l'événement.
 *
 * <p>
 * {@code @DirtiesContext} : {@code RoomService.warmRooms()} accumule dans un
 * cache mémoire jamais vidé, que {@code @Transactional} ne peut pas annuler (ce
 * n'est pas de l'état DB) — sans ça, la starting room insérée ici pollue le
 * singleton {@code RoomService} pour les classes de test suivantes (ex:
 * {@code RoomServiceTest#startingRoomIsEmptyWhenNoRoomIsMarkedAsStarting}).
 */
@Transactional
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class GameWorldTest extends AbstractIntegrationTest {

    @Autowired
    private GameWorld gameWorld;

    @Autowired
    private AccountDao accountDao;

    @Autowired
    private RoomDao roomDao;

    @Autowired
    private RoomService roomService;

    @Autowired
    private CharacterDao characterDao;

    @Test
    void createCharacterRollsScoresAppliesRaceBonusesPersistsAndSpawnsToTheStartingRoom() {
        Account account = new Account(UUID.randomUUID(), "hilde", "hashed-password", null);
        accountDao.insert(account);
        Room startingRoom = new Room(UUID.randomUUID(), "Place du village", "...", true);
        roomDao.insert(startingRoom);
        roomService.warmRooms();

        Character character = gameWorld.createCharacter(account, "Hilde", Race.HUMAN);

        assertThat(characterDao.findById(character.getId())).contains(character);
        assertThat(character.getLevel()).isEqualTo(1);
        assertThat(character.getCurrentHealth()).isEqualTo(100);
        assertThat(character.getMaxHealth()).isEqualTo(100);
        // Human: +1 to all six attributes on top of a 4d6-drop-lowest roll (3-18).
        assertThat(character.getAttribute(Attribute.STRENGTH)).isBetween(4, 19);
        assertThat(character.getAttribute(Attribute.DEXTERITY)).isBetween(4, 19);
        assertThat(character.getAttribute(Attribute.CONSTITUTION)).isBetween(4, 19);
        assertThat(character.getAttribute(Attribute.INTELLIGENCE)).isBetween(4, 19);
        assertThat(character.getAttribute(Attribute.WISDOM)).isBetween(4, 19);
        assertThat(character.getAttribute(Attribute.CHARISMA)).isBetween(4, 19);
        assertThat(character.getCurrentRoom()).isEqualTo(startingRoom);
    }
}
