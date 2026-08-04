package fr.idev.mudserver.game;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import fr.idev.mudserver.AbstractIntegrationTest;
import fr.idev.mudserver.domain.Account;
import fr.idev.mudserver.domain.Attribute;
import fr.idev.mudserver.domain.GamePlayer;
import fr.idev.mudserver.domain.CharacterClass;
import fr.idev.mudserver.domain.Race;
import fr.idev.mudserver.domain.Room;
import fr.idev.mudserver.persistence.AccountDao;
import fr.idev.mudserver.persistence.CharacterDao;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Non-régression : GameWorld#createCharacter publie NewGamePlayerCreated, dont
 * le listener onNewGamePlayerCreated (dans cette même classe) fait le
 * characterDao.insert — ce test prouve que la persistance a bien lieu malgré
 * l'indirection par l'événement.
 *
 * <p>
 * Pas besoin de {@code @DirtiesContext} : {@code RoomService.warmRooms()}
 * recharge désormais le même ensemble fixe de rooms depuis
 * {@code data/rooms.json} à chaque appel (chaque room est reconstruite, pas
 * accumulée), donc rejouer {@code warmRooms()} ici ne laisse aucun état
 * arbitraire qui pourrait polluer le singleton {@code RoomService} pour les
 * classes de test suivantes.
 */
@Transactional
class GameWorldTest extends AbstractIntegrationTest {

    @Autowired
    private GameWorld gameWorld;

    @Autowired
    private AccountDao accountDao;

    @Autowired
    private RoomService roomService;

    @Autowired
    private RaceService raceService;

    @Autowired
    private ClassService classService;

    @Autowired
    private CharacterDao characterDao;

    @Test
    void createCharacterRollsScoresAppliesRaceBonusesPersistsAndSpawnsToTheStartingRoom() {
        Account account = new Account(UUID.randomUUID(), "hilde", "hashed-password", null);
        accountDao.insert(account);
        roomService.warmRooms();
        Room startingRoom = roomService.startingRoom().orElseThrow();
        raceService.warmRaceBonuses();
        classService.warmClassHitDice();

        GamePlayer character = gameWorld.createCharacter(account, "Hilde", Race.HUMAN, CharacterClass.FIGHTER);

        assertThat(characterDao.findById(character.getId())).contains(character);
        assertThat(character.getLevel()).isEqualTo(1);
        int expectedConstitutionModifier = character.getModifier(Attribute.CONSTITUTION);
        int expectedMaxHealth = Math.max(1, classService.hitDie(CharacterClass.FIGHTER) + expectedConstitutionModifier);
        assertThat(character.getMaxHealth()).isEqualTo(expectedMaxHealth);
        assertThat(character.getCurrentHealth()).isEqualTo(expectedMaxHealth);
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
