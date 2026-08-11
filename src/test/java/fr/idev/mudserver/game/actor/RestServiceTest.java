package fr.idev.mudserver.game.actor;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import fr.idev.mudserver.AbstractIntegrationTest;
import fr.idev.mudserver.controller.RecordingConnection;
import fr.idev.mudserver.domain.Account;
import fr.idev.mudserver.domain.Item;
import fr.idev.mudserver.domain.RoomInstance;
import fr.idev.mudserver.domain.WorldInstance;
import fr.idev.mudserver.domain.actor.CharacterClass;
import fr.idev.mudserver.domain.actor.GameMonster;
import fr.idev.mudserver.domain.actor.Gender;
import fr.idev.mudserver.domain.actor.GamePlayer;
import fr.idev.mudserver.domain.actor.MonsterTemplate;
import fr.idev.mudserver.domain.actor.Race;
import fr.idev.mudserver.domain.actor.TestAttributes;
import fr.idev.mudserver.game.CombatEngine;
import fr.idev.mudserver.game.GameWorld;
import fr.idev.mudserver.game.ItemService;
import fr.idev.mudserver.game.RoomService;
import fr.idev.mudserver.persistence.AccountDao;
import fr.idev.mudserver.persistence.CharacterDao;
import fr.idev.mudserver.persistence.ItemDao;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class RestServiceTest extends AbstractIntegrationTest {

    // Templates réels de data/items.json — Pomme (FOOD, valeur 5), Oeufs (FOOD,
    // valeur 15).
    private static final UUID APPLE_TEMPLATE_ID = UUID.fromString("b4b5f22f-0fd1-43a6-b1f0-7ac0103478d6");
    private static final UUID EGGS_TEMPLATE_ID = UUID.fromString("95c33240-6454-408f-9584-cfb45427db84");

    @Autowired
    private RestService restService;

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

    @Autowired
    private CombatEngine combatEngine;

    @Test
    void shortRestHealsEveryOnlinePlayerWithTheirOwnFormula() {
        // FIGHTER (hitDie 10) et WIZARD (hitDie 6), CON 10 (modificateur nul) :
        // hpGain = hitDie/2 + 1 = 6 et 4 respectivement.
        RecordingConnection aConnection = enterGame(1, 100, CharacterClass.FIGHTER);
        GamePlayer a = gameWorld.character(aConnection);
        RecordingConnection bConnection = enterGame(1, 100, CharacterClass.WIZARD);
        GamePlayer b = gameWorld.character(bConnection);

        RestService.RestOutcome outcome = restService.shortRest(a);

        assertThat(outcome).isInstanceOf(RestService.RestOutcome.Rested.class);
        assertThat(a.getCurrentHealth()).isEqualTo(7);
        assertThat(b.getCurrentHealth()).isEqualTo(5);
        assertThat(a.getShortRestCount()).isEqualTo(1);
        assertThat(b.getShortRestCount()).isEqualTo(1);
    }

    @Test
    void shortRestIsRefusedPastTheCapAndNothingChanges() {
        RecordingConnection connection = enterGame(1, 100, CharacterClass.FIGHTER);
        GamePlayer character = gameWorld.character(connection);
        restService.shortRest(character);
        restService.shortRest(character);
        int healthAfterTwoRests = character.getCurrentHealth();

        RestService.RestOutcome outcome = restService.shortRest(character);

        assertThat(outcome).isInstanceOf(RestService.RestOutcome.NoShortRestLeft.class);
        assertThat(character.getCurrentHealth()).isEqualTo(healthAfterTwoRests);
        assertThat(character.getShortRestCount()).isEqualTo(2);
    }

    @Test
    void shortRestIsRefusedInCombatAndNothingChanges() {
        RoomInstance room = startingRoom();
        RecordingConnection connection = enterGameInRoom(1, 100, CharacterClass.FIGHTER, room);
        GamePlayer character = gameWorld.character(connection);
        GameMonster monster = monster(room);
        combatEngine.attack(character, monster);

        RestService.RestOutcome outcome = restService.shortRest(character);

        assertThat(outcome).isInstanceOf(RestService.RestOutcome.InCombat.class);
        assertThat(character.getShortRestCount()).isZero();
        assertThat(character.getCurrentHealth()).isEqualTo(1);
    }

    @Test
    void longRestFailsUnderTheProvisionThresholdAndConsumesNothing() {
        RecordingConnection connection = enterGame(1, 100, CharacterClass.FIGHTER);
        GamePlayer character = gameWorld.character(connection);
        Item apple = addToInventory(character, APPLE_TEMPLATE_ID);

        RestService.RestOutcome outcome = restService.longRest(character, List.of(apple));

        assertThat(outcome).isEqualTo(new RestService.RestOutcome.NotEnoughProvisions(5));
        assertThat(character.getInventory().getItems()).contains(apple);
        assertThat(itemDao.findById(apple.getId())).isPresent();
        assertThat(character.getCurrentHealth()).isEqualTo(1);
    }

    @Test
    void longRestFullyHealsEveryoneOnlineResetsTheCounterAndConsumesTheFood() {
        RecordingConnection aConnection = enterGame(1, 100, CharacterClass.FIGHTER);
        GamePlayer a = gameWorld.character(aConnection);
        RecordingConnection bConnection = enterGame(1, 50, CharacterClass.WIZARD);
        GamePlayer b = gameWorld.character(bConnection);
        restService.shortRest(a); // fait monter les deux compteurs à 1, pour prouver le reset

        Item eggs = addToInventory(a, EGGS_TEMPLATE_ID); // 15
        Item apple = addToInventory(a, APPLE_TEMPLATE_ID); // 5, total 20

        RestService.RestOutcome outcome = restService.longRest(a, List.of(eggs, apple));

        assertThat(outcome).isInstanceOf(RestService.RestOutcome.Rested.class);
        assertThat(a.getCurrentHealth()).isEqualTo(a.getMaxHealth());
        assertThat(b.getCurrentHealth()).isEqualTo(b.getMaxHealth());
        assertThat(a.getShortRestCount()).isZero();
        assertThat(b.getShortRestCount()).isZero();
        assertThat(a.getInventory().getItems()).doesNotContain(eggs).doesNotContain(apple);
        assertThat(itemDao.findById(eggs.getId())).isEmpty();
        assertThat(itemDao.findById(apple.getId())).isEmpty();
    }

    @Test
    void longRestIsRefusedInCombatAndConsumesNothing() {
        RoomInstance room = startingRoom();
        RecordingConnection connection = enterGameInRoom(1, 100, CharacterClass.FIGHTER, room);
        GamePlayer character = gameWorld.character(connection);
        GameMonster monster = monster(room);
        combatEngine.attack(character, monster);
        Item eggs = addToInventory(character, EGGS_TEMPLATE_ID);

        RestService.RestOutcome outcome = restService.longRest(character, List.of(eggs));

        assertThat(outcome).isInstanceOf(RestService.RestOutcome.InCombat.class);
        assertThat(character.getInventory().getItems()).contains(eggs);
        assertThat(character.getCurrentHealth()).isEqualTo(1);
    }

    private Item addToInventory(GamePlayer character, UUID templateId) {
        itemService.warmItemTemplates();
        Item item = new Item(UUID.randomUUID(), templateId, null, character.getId(), null);
        itemDao.insert(item);
        character.getInventory().replaceItems(itemService.loadInventory(character));
        return character.getInventory().getItems().stream().filter(i -> i.getId().equals(item.getId())).findFirst()
                .orElseThrow();
    }

    private RoomInstance startingRoom() {
        roomService.warmRooms();
        return roomService.startingRoom().orElseThrow();
    }

    private RecordingConnection enterGame(int currentHealth, int maxHealth, CharacterClass characterClass) {
        return enterGameInRoom(currentHealth, maxHealth, characterClass, startingRoom());
    }

    private RecordingConnection enterGameInRoom(int currentHealth, int maxHealth, CharacterClass characterClass,
            RoomInstance room) {
        Account account = new Account(UUID.randomUUID(), "utilisateur-" + UUID.randomUUID(), "hashed-password", null);
        accountDao.insert(account);
        RecordingConnection connection = new RecordingConnection();
        GamePlayer character = new GamePlayer(UUID.randomUUID(), account.getId(), "Repos-" + UUID.randomUUID(),
                room.getId(), Gender.MAN, Race.HUMAN, characterClass, 1, currentHealth, maxHealth,
                TestAttributes.of(10, 10, 10, 10, 10, 10), 0, 0);
        character.setWorldInstanceId(WorldInstance.DEFAULT_ID);
        characterDao.insert(character);
        gameWorld.enterWorld(connection, character);
        connection.received.clear();
        return connection;
    }

    private GameMonster monster(RoomInstance room) {
        // FORCE -10 (modificateur -10) : un 20 naturel touche toujours quelle que
        // soit la CA (règle 5e, voir DiceRoller#resolveHit), donc l'AC à -1000
        // ci-dessous ne suffit pas à garantir que le mannequin ne touche jamais en
        // retour lors de la riposte déclenchée par CombatEngine#cascade — sans ce
        // modificateur, ~1 attaque sur 20 blesse le personnage à 1 PV et fait
        // échouer les assertions "rien n'a changé" (flaky, voir multi-world.md).
        // Même avec un coup critique (dégâts doublés), le plancher Math.max(0, ...)
        // de GameMonster#rollDamage garantit 0 dégât : 2d4 (max 8) - 10 < 0.
        MonsterTemplate template = new MonsterTemplate(UUID.randomUUID(), "Mannequin " + UUID.randomUUID(),
                "Un mannequin d'entraînement", 1000, TestAttributes.of(-10, 10, 10, 10, 10, 10), -1000, 0, "1d4", 0,
                List.of(), 0);
        GameMonster monster = new GameMonster(UUID.randomUUID(), template.getName(), template.getId(), room.getId(),
                template.getAttributes(), 1000);
        monster.attachTemplate(template);
        monster.setCurrentRoom(room);
        room.addMonster(monster);
        return monster;
    }
}
