package fr.idev.mudserver.controller.ingame;

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
import fr.idev.mudserver.network.message.Usage;
import fr.idev.mudserver.network.message.ingame.CannotRestInCombat;
import fr.idev.mudserver.network.message.ingame.HpRestored;
import fr.idev.mudserver.network.message.ingame.LongRestAnnounced;
import fr.idev.mudserver.network.message.ingame.LongRestCancelled;
import fr.idev.mudserver.network.message.ingame.NoProvisionsAvailable;
import fr.idev.mudserver.network.message.ingame.NoShortRestsLeft;
import fr.idev.mudserver.network.message.ingame.NotEnoughProvisions;
import fr.idev.mudserver.network.message.ingame.ShortRestAnnounced;
import java.util.List;
import fr.idev.mudserver.persistence.AccountDao;
import fr.idev.mudserver.persistence.CharacterDao;
import fr.idev.mudserver.persistence.ItemDao;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class RestTest extends AbstractIntegrationTest {

    // Templates réels de data/items.json — Pomme (FOOD, valeur 5), Oeufs (FOOD,
    // valeur 15).
    private static final UUID APPLE_TEMPLATE_ID = UUID.fromString("b4b5f22f-0fd1-43a6-b1f0-7ac0103478d6");
    private static final UUID EGGS_TEMPLATE_ID = UUID.fromString("95c33240-6454-408f-9584-cfb45427db84");

    @Autowired
    private Rest rest;

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
    void invalidArgumentSendsUsage() {
        RecordingConnection connection = enterGame(50, 100);

        rest.onReceive(connection, "sideways");

        assertThat(connection.received).containsExactly(new Usage("rest <short|long>"));
    }

    @Test
    void shortRestHealsAndAnnouncesToEveryoneOnline() {
        RecordingConnection connection = enterGame(1, 100);
        GamePlayer character = gameWorld.character(connection);

        rest.onReceive(connection, "short");

        assertThat(character.getCurrentHealth()).isGreaterThan(1);
        assertThat(connection.received).anyMatch(HpRestored.class::isInstance)
                .anyMatch(ShortRestAnnounced.class::isInstance);
    }

    @Test
    void thirdShortRestIsRefused() {
        RecordingConnection connection = enterGame(1, 100);
        rest.onReceive(connection, "short");
        rest.onReceive(connection, "short");
        connection.received.clear();

        rest.onReceive(connection, "short");

        assertThat(connection.received).containsExactly(new NoShortRestsLeft());
    }

    @Test
    void shortRestInCombatIsRefused() {
        RoomInstance room = startingRoom();
        RecordingConnection connection = enterGameInRoom(1, 100, room);
        GamePlayer character = gameWorld.character(connection);
        GameMonster monster = monster(room);
        combatEngine.attack(character, monster);
        connection.received.clear();

        rest.onReceive(connection, "short");

        assertThat(connection.received).containsExactly(new CannotRestInCombat());
    }

    @Test
    void longRestWithNoFoodSendsNoProvisionsAvailable() {
        RecordingConnection connection = enterGame(1, 100);

        rest.onReceive(connection, "long");

        assertThat(connection.received).containsExactly(new NoProvisionsAvailable());
    }

    @Test
    void longRestWithEnoughSelectedProvisionsFullyHealsAndConsumesThem() {
        RecordingConnection connection = enterGame(1, 100);
        GamePlayer character = gameWorld.character(connection);
        Item eggs = addToInventory(character, EGGS_TEMPLATE_ID); // 15
        Item apple = addToInventory(character, APPLE_TEMPLATE_ID); // 5, total 20
        connection.queueAnswer("Oeufs");
        connection.queueAnswer("Pomme");
        connection.queueAnswer("done");

        rest.onReceive(connection, "long");

        assertThat(character.getCurrentHealth()).isEqualTo(character.getMaxHealth());
        assertThat(character.getInventory().getItems()).doesNotContain(eggs).doesNotContain(apple);
        assertThat(itemDao.findById(eggs.getId())).isEmpty();
        assertThat(itemDao.findById(apple.getId())).isEmpty();
        assertThat(connection.received).anyMatch(HpRestored.class::isInstance)
                .anyMatch(LongRestAnnounced.class::isInstance);
    }

    @Test
    void longRestWithNotEnoughSelectedProvisionsConsumesNothing() {
        RecordingConnection connection = enterGame(1, 100);
        GamePlayer character = gameWorld.character(connection);
        Item apple = addToInventory(character, APPLE_TEMPLATE_ID); // 5 < 20
        connection.queueAnswer("Pomme");
        connection.queueAnswer("done");

        rest.onReceive(connection, "long");

        assertThat(character.getCurrentHealth()).isEqualTo(1);
        assertThat(character.getInventory().getItems()).contains(apple);
        assertThat(itemDao.findById(apple.getId())).isPresent();
        assertThat(connection.received).anyMatch(NotEnoughProvisions.class::isInstance);
    }

    @Test
    void longRestCanBeCancelledWithoutConsumingAnything() {
        RecordingConnection connection = enterGame(1, 100);
        GamePlayer character = gameWorld.character(connection);
        Item eggs = addToInventory(character, EGGS_TEMPLATE_ID);
        connection.queueAnswer("Oeufs");
        connection.queueAnswer("cancel");

        rest.onReceive(connection, "long");

        assertThat(character.getCurrentHealth()).isEqualTo(1);
        assertThat(character.getInventory().getItems()).contains(eggs);
        assertThat(connection.received).anyMatch(LongRestCancelled.class::isInstance);
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

    private RecordingConnection enterGame(int currentHealth, int maxHealth) {
        return enterGameInRoom(currentHealth, maxHealth, startingRoom());
    }

    private RecordingConnection enterGameInRoom(int currentHealth, int maxHealth, RoomInstance room) {
        Account account = new Account(UUID.randomUUID(), "utilisateur-" + UUID.randomUUID(), "hashed-password", null);
        accountDao.insert(account);
        RecordingConnection connection = new RecordingConnection();
        GamePlayer character = new GamePlayer(UUID.randomUUID(), account.getId(), "Repos-" + UUID.randomUUID(),
                room.getId(), Gender.MAN, Race.HUMAN, CharacterClass.FIGHTER, 1, currentHealth, maxHealth,
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
