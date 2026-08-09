package fr.idev.mudserver.controller.ingame;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import fr.idev.mudserver.AbstractIntegrationTest;
import fr.idev.mudserver.controller.RecordingConnection;
import fr.idev.mudserver.domain.Account;
import fr.idev.mudserver.domain.Item;
import fr.idev.mudserver.domain.Rarity;
import fr.idev.mudserver.domain.Room;
import fr.idev.mudserver.domain.actor.CharacterClass;
import fr.idev.mudserver.domain.actor.GameMonster;
import fr.idev.mudserver.domain.actor.Gender;
import fr.idev.mudserver.domain.actor.GamePlayer;
import fr.idev.mudserver.domain.actor.MonsterTemplate;
import fr.idev.mudserver.domain.actor.Race;
import fr.idev.mudserver.domain.actor.TestAttributes;
import fr.idev.mudserver.domain.actor.TestProficiencies;
import fr.idev.mudserver.game.CombatEngine;
import fr.idev.mudserver.game.GameWorld;
import fr.idev.mudserver.game.ItemService;
import fr.idev.mudserver.game.RoomService;
import fr.idev.mudserver.network.message.Usage;
import fr.idev.mudserver.network.message.ingame.ItemNotCarried;
import fr.idev.mudserver.network.message.ingame.ItemNotUsable;
import fr.idev.mudserver.network.message.ingame.ItemUsed;
import fr.idev.mudserver.network.message.ingame.MonsterAttackResult;
import fr.idev.mudserver.network.message.ingame.NotYourTurn;
import fr.idev.mudserver.persistence.AccountDao;
import fr.idev.mudserver.persistence.CharacterDao;
import fr.idev.mudserver.persistence.ItemDao;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class UseTest extends AbstractIntegrationTest {

    // Templates réels de data/items.json — Potion de soin (POTION, HEALING,
    // 2d4+2) et Epée courte (WEAPON, pas un ConsumableItem).
    private static final UUID POTION_TEMPLATE_ID = UUID.fromString("019fa0a5-80bf-7e84-87bf-5cf699c00315");
    private static final UUID SWORD_TEMPLATE_ID = UUID.fromString("019fa0a5-80c0-7035-9c2d-113b09a275df");

    @Autowired
    private Use use;

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
    void emptyArgumentSendsUsage() {
        RecordingConnection connection = enterGame(10, 10, 10);

        use.onReceive(connection, "");

        assertThat(connection.received).containsExactly(new Usage("use <item name>"));
    }

    @Test
    void itemNotCarriedSendsItemNotCarried() {
        RecordingConnection connection = enterGame(10, 10, 10);

        use.onReceive(connection, "Potion de soin");

        assertThat(connection.received).containsExactly(new ItemNotCarried("Potion de soin"));
    }

    @Test
    void nonConsumableItemSendsItemNotUsable() {
        RecordingConnection connection = enterGame(10, 10, 10);
        GamePlayer character = gameWorld.character(connection);
        Item sword = addToInventory(character, SWORD_TEMPLATE_ID);

        use.onReceive(connection, "Epée courte");

        assertThat(connection.received).containsExactly(new ItemNotUsable("Epée courte"));
        assertThat(character.getInventory().getItems()).contains(sword);
    }

    @Test
    void outOfCombatHealsTheCharacterAndConsumesThePotion() {
        RecordingConnection connection = enterGame(5, 50, 10);
        GamePlayer character = gameWorld.character(connection);
        Item potion = addToInventory(character, POTION_TEMPLATE_ID);

        use.onReceive(connection, "Potion de soin");

        ItemUsed message = (ItemUsed) connection.received.stream().filter(ItemUsed.class::isInstance).findFirst()
                .orElseThrow();
        assertThat(message.healedAmount()).isBetween(4, 10); // 2d4+2
        assertThat(character.getCurrentHealth()).isEqualTo(5 + message.healedAmount());
        assertThat(character.getInventory().getItems()).doesNotContain(potion);
        assertThat(itemDao.findById(potion.getId())).isEmpty();
    }

    @Test
    void outOfCombatAtFullHealthStillConsumesThePotionWithoutOverhealing() {
        RecordingConnection connection = enterGame(1000, 1000, 10);
        GamePlayer character = gameWorld.character(connection);
        addToInventory(character, POTION_TEMPLATE_ID);

        use.onReceive(connection, "Potion de soin");

        assertThat(connection.received).contains(new ItemUsed("Potion de soin", Rarity.COMMON, 0, 1000, 1000));
        assertThat(character.getCurrentHealth()).isEqualTo(1000);
        assertThat(character.getInventory().getItems()).isEmpty();
    }

    @Test
    void duringCombatHealsAndConsumesTheTurn() {
        Room room = startingRoom();
        // DEX 100 vs DEX 10 : le joueur gagne quasi systématiquement l'initiative,
        // même convention que CombatEngineTest.
        RecordingConnection connection = enterGameInRoom(50, 1000, 100, room);
        GamePlayer character = gameWorld.character(connection);
        GameMonster monster = monster(room, 10, "1d4");
        Item potion = addToInventory(character, POTION_TEMPLATE_ID);

        combatEngine.attack(character, monster);
        assertThat(character.getEncounter().currentParticipant()).as("player should act first with DEX 100")
                .isEqualTo(character);
        connection.received.clear();

        use.onReceive(connection, "Potion de soin");

        assertThat(connection.received).anyMatch(ItemUsed.class::isInstance);
        assertThat(character.getInventory().getItems()).doesNotContain(potion);
        assertThat(connection.received).as("the monster's turn must have resolved as part of the cascade")
                .anyMatch(MonsterAttackResult.class::isInstance);
        assertThat(character.getEncounter().currentParticipant()).as("turn came back around to the player")
                .isEqualTo(character);
    }

    @Test
    void duringCombatWhenNotYourTurnSendsNotYourTurnAndKeepsTheItem() {
        Room room = startingRoom();
        RecordingConnection aConnection = enterGameInRoom(1000, 1000, 100, room);
        GamePlayer a = gameWorld.character(aConnection);
        GameMonster monster = monster(room, 10, "1d4");
        combatEngine.attack(a, monster);
        assertThat(a.getEncounter().currentParticipant()).isEqualTo(a);

        RecordingConnection bConnection = enterGameInRoom(1000, 1000, 10, room);
        GamePlayer b = gameWorld.character(bConnection);
        Item potion = addToInventory(b, POTION_TEMPLATE_ID);
        combatEngine.attack(b, monster); // rejoint l'affrontement, n'agit pas encore
        bConnection.received.clear();

        use.onReceive(bConnection, "Potion de soin");

        assertThat(bConnection.received).containsExactly(new NotYourTurn());
        assertThat(b.getInventory().getItems()).contains(potion);
    }

    private Item addToInventory(GamePlayer character, UUID templateId) {
        itemService.warmItemTemplates();
        Item item = new Item(UUID.randomUUID(), templateId, null, character.getId(), null);
        itemDao.insert(item);
        character.getInventory().replaceItems(itemService.loadInventory(character));
        return character.getInventory().getItems().stream().filter(i -> i.getId().equals(item.getId())).findFirst()
                .orElseThrow();
    }

    private Room startingRoom() {
        roomService.warmRooms();
        return roomService.startingRoom().orElseThrow();
    }

    private RecordingConnection enterGame(int currentHealth, int maxHealth, int dexterity) {
        return enterGameInRoom(currentHealth, maxHealth, dexterity, startingRoom());
    }

    private RecordingConnection enterGameInRoom(int currentHealth, int maxHealth, int dexterity, Room room) {
        Account account = new Account(UUID.randomUUID(), "utilisateur-" + UUID.randomUUID(), "hashed-password", null);
        accountDao.insert(account);
        RecordingConnection connection = new RecordingConnection();
        GamePlayer character = new GamePlayer(UUID.randomUUID(), account.getId(), "Buveur-" + UUID.randomUUID(),
                room.getId(), Gender.MAN, Race.HUMAN, CharacterClass.FIGHTER,
                TestProficiencies.primaryAbility(CharacterClass.FIGHTER),
                TestProficiencies.savingThrows(CharacterClass.FIGHTER),
                TestProficiencies.skills(CharacterClass.FIGHTER),
                TestProficiencies.weaponProficiencies(CharacterClass.FIGHTER),
                TestProficiencies.armorProficiencies(CharacterClass.FIGHTER), 1, currentHealth, maxHealth,
                TestAttributes.of(10, dexterity, 10, 10, 10, 10), 0, 0);
        characterDao.insert(character);
        gameWorld.enterWorld(connection, character);
        connection.received.clear();
        return connection;
    }

    private GameMonster monster(Room room, int dexterity, String naturalDamageDice) {
        MonsterTemplate template = new MonsterTemplate(UUID.randomUUID(), "Mannequin " + UUID.randomUUID(),
                "Un mannequin d'entraînement", 1000, TestAttributes.of(10, dexterity, 10, 10, 10, 10), -1000, 0,
                naturalDamageDice, 0, List.of(), 0);
        GameMonster monster = new GameMonster(UUID.randomUUID(), template.getName(), template.getId(), room.getId(),
                template.getAttributes(), 1000);
        monster.attachTemplate(template);
        monster.setCurrentRoom(room);
        room.addMonster(monster);
        return monster;
    }
}
