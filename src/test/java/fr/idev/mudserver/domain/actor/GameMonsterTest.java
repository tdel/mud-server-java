package fr.idev.mudserver.domain.actor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import fr.idev.mudserver.AbstractIntegrationTest;
import fr.idev.mudserver.domain.Account;
import fr.idev.mudserver.domain.Room;
import fr.idev.mudserver.game.CombatResult;
import fr.idev.mudserver.game.ItemService;
import fr.idev.mudserver.network.Connection;
import fr.idev.mudserver.network.ConnectionState;
import fr.idev.mudserver.network.OutputMessage;
import fr.idev.mudserver.network.message.ingame.EquipmentLooted;
import fr.idev.mudserver.network.message.ingame.GoldLooted;
import fr.idev.mudserver.network.message.ingame.MonsterDefeated;
import fr.idev.mudserver.persistence.AccountDao;
import fr.idev.mudserver.persistence.CharacterDao;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contexte Spring requis (pas {@code @Transactional}, sur le modèle de
 * {@code ItemRaceConditionTest}) : sur le coup fatal,
 * {@link GameMonster#takeDamage} publie {@code CharacterDied} via le holder
 * statique {@code DomainEventPublisher}, qui suppose ce contexte initialisé —
 * et le test de concurrence ci-dessous ne doit pas partager la
 * connexion/transaction du thread de test avec les écritures que cet événement
 * déclenche (même raison que {@code ItemRaceConditionTest}).
 */
class GameMonsterTest extends AbstractIntegrationTest {

    private static final UUID HEALING_POTION_TEMPLATE_ID = UUID.fromString("019fa0a5-80bf-7e84-87bf-5cf699c00315");
    private static final UUID SHORT_SWORD_TEMPLATE_ID = UUID.fromString("019fa0a5-80c0-7035-9c2d-113b09a275df");

    @Autowired
    private ItemService itemService;

    @Autowired
    private AccountDao accountDao;

    @Autowired
    private CharacterDao characterDao;

    @Test
    void armorClassUsesTheTemplateNaturalArmorClassWhenSet() {
        GameMonster monster = monster(TestAttributes.of(10, 14, 10, 10, 10, 10), 15);

        assertThat(monster.getArmorClass()).isEqualTo(15);
    }

    @Test
    void armorClassFallsBackToTenPlusDexModifierWhenTemplateHasNoNaturalArmorClass() {
        GameMonster monster = monster(TestAttributes.of(10, 14, 10, 10, 10, 10), null);

        assertThat(monster.getArmorClass()).isEqualTo(12);
    }

    @Test
    void takeDamageReducesHealthWithoutGoingBelowZero() {
        GameMonster monster = monster(TestAttributes.of(10, 10, 10, 10, 10, 10), null);
        GamePlayer attacker = player("Attaquant");

        assertThat(monster.takeDamage(3, attacker)).isFalse();
        assertThat(monster.getCurrentHealth()).isEqualTo(4);

        assertThat(monster.takeDamage(100, attacker)).isTrue();
        assertThat(monster.getCurrentHealth()).isZero();
    }

    @Test
    void takeDamageAfterDeathIsANoOpAndNeverReportsAnotherKillingBlow() {
        GameMonster monster = monster(TestAttributes.of(10, 10, 10, 10, 10, 10), null);
        GamePlayer attacker = player("Attaquant");
        monster.takeDamage(100, attacker);

        assertThat(monster.takeDamage(5, attacker)).isFalse();
        assertThat(monster.getCurrentHealth()).isZero();
    }

    @Test
    void exactlyOneConcurrentAttackLandsTheKillingBlow() throws Exception {
        int attackers = 20;
        GameMonster monster = monster(TestAttributes.of(10, 10, 10, 10, 10, 10), null);
        GamePlayer attacker = player("Attaquant");

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            CyclicBarrier barrier = new CyclicBarrier(attackers);
            List<Future<Boolean>> results = new ArrayList<>();
            for (int i = 0; i < attackers; i++) {
                results.add(executor.submit(() -> {
                    barrier.await();
                    return monster.takeDamage(100, attacker);
                }));
            }

            long killingBlows = 0;
            for (Future<Boolean> result : results) {
                if (result.get()) {
                    killingBlows++;
                }
            }

            assertThat(killingBlows).as("un seul attaquant doit porter le coup fatal").isEqualTo(1);
        }
        assertThat(monster.getCurrentHealth()).isZero();
    }

    @Test
    void aKillingBlowRemovesTheMonsterFromItsRoomAndBroadcastsToEveryoneIncludingTheAttacker() {
        GameMonster monster = monster(TestAttributes.of(10, 10, 10, 10, 10, 10), null);
        Room room = monster.getCurrentRoom();
        GamePlayer attacker = player("Attaquant");
        GamePlayer bystander = player("Témoin");
        RecordingConnection attackerConnection = new RecordingConnection();
        RecordingConnection bystanderConnection = new RecordingConnection();
        attacker.setConnection(attackerConnection);
        bystander.setConnection(bystanderConnection);
        room.join(attacker);
        room.join(bystander);
        attackerConnection.received.clear();
        bystanderConnection.received.clear();

        monster.takeDamage(100, attacker);

        assertThat(room.getMonsters()).doesNotContain(monster);
        assertThat(attackerConnection.received).anyMatch(MonsterDefeated.class::isInstance);
        assertThat(bystanderConnection.received).anyMatch(MonsterDefeated.class::isInstance);
    }

    @Test
    void aKillingBlowGrantsTheMonsterXpRewardAndClearsTheAttackersTarget() {
        GameMonster monster = monster(TestAttributes.of(10, 10, 10, 10, 10, 10), null, 50);
        GamePlayer attacker = player("Attaquant");
        attacker.setTarget(monster);
        monster.getCurrentRoom().join(attacker);

        monster.takeDamage(100, attacker);

        assertThat(attacker.getXp()).isEqualTo(50);
        assertThat(attacker.getTarget()).isNull();
    }

    @Test
    void aKillingBlowGrantsTheMonstersGoldRewardToTheKillerOnly() {
        GameMonster monster = monster(TestAttributes.of(10, 10, 10, 10, 10, 10), null, 0, 25, List.of());
        GamePlayer attacker = player("Attaquant");
        RecordingConnection attackerConnection = new RecordingConnection();
        attacker.setConnection(attackerConnection);
        monster.getCurrentRoom().join(attacker);

        monster.takeDamage(100, attacker);

        assertThat(attacker.getInventory().getGold()).isEqualTo(25);
        assertThat(attackerConnection.received).contains(new GoldLooted(25));
    }

    @Test
    void aKillingBlowWithFullDropChanceAlwaysGrantsTheLootedItem() {
        itemService.warmItemTemplates();
        GameMonster monster = monster(TestAttributes.of(10, 10, 10, 10, 10, 10), null, 0, 0,
                List.of(new MonsterTemplate.LootTableEntry(HEALING_POTION_TEMPLATE_ID, 1.0)));
        GamePlayer attacker = persistedPlayer("Attaquant");
        RecordingConnection attackerConnection = new RecordingConnection();
        attacker.setConnection(attackerConnection);
        monster.getCurrentRoom().join(attacker);

        monster.takeDamage(100, attacker);

        assertThat(attacker.getInventory().getItems())
                .anySatisfy(item -> assertThat(item.getTemplateId()).isEqualTo(HEALING_POTION_TEMPLATE_ID));
        assertThat(attackerConnection.received).anyMatch(EquipmentLooted.class::isInstance);
    }

    @Test
    void aKillingBlowWithZeroDropChanceNeverGrantsTheLootedItem() {
        itemService.warmItemTemplates();
        GameMonster monster = monster(TestAttributes.of(10, 10, 10, 10, 10, 10), null, 0, 0,
                List.of(new MonsterTemplate.LootTableEntry(HEALING_POTION_TEMPLATE_ID, 0.0)));
        GamePlayer attacker = player("Attaquant");
        RecordingConnection attackerConnection = new RecordingConnection();
        attacker.setConnection(attackerConnection);
        monster.getCurrentRoom().join(attacker);

        monster.takeDamage(100, attacker);

        assertThat(attacker.getInventory().getItems()).isEmpty();
        assertThat(attackerConnection.received).noneMatch(EquipmentLooted.class::isInstance);
    }

    @Test
    void aKillingBlowCanGrantSeveralLootedItemsAtOnce() {
        itemService.warmItemTemplates();
        GameMonster monster = monster(TestAttributes.of(10, 10, 10, 10, 10, 10), null, 0, 0,
                List.of(new MonsterTemplate.LootTableEntry(HEALING_POTION_TEMPLATE_ID, 1.0),
                        new MonsterTemplate.LootTableEntry(SHORT_SWORD_TEMPLATE_ID, 1.0)));
        GamePlayer attacker = persistedPlayer("Attaquant");
        attacker.setConnection(new RecordingConnection());
        monster.getCurrentRoom().join(attacker);

        monster.takeDamage(100, attacker);

        assertThat(attacker.getInventory().getItems()).extracting("templateId")
                .containsExactlyInAnyOrder(HEALING_POTION_TEMPLATE_ID, SHORT_SWORD_TEMPLATE_ID);
    }

    @Test
    void aKillingBlowNeverSendsLootMessagesToBystanders() {
        itemService.warmItemTemplates();
        GameMonster monster = monster(TestAttributes.of(10, 10, 10, 10, 10, 10), null, 0, 25,
                List.of(new MonsterTemplate.LootTableEntry(HEALING_POTION_TEMPLATE_ID, 1.0)));
        Room room = monster.getCurrentRoom();
        GamePlayer attacker = persistedPlayer("Attaquant");
        GamePlayer bystander = player("Témoin");
        RecordingConnection attackerConnection = new RecordingConnection();
        RecordingConnection bystanderConnection = new RecordingConnection();
        attacker.setConnection(attackerConnection);
        bystander.setConnection(bystanderConnection);
        room.join(attacker);
        room.join(bystander);
        attackerConnection.received.clear();
        bystanderConnection.received.clear();

        monster.takeDamage(100, attacker);

        assertThat(attackerConnection.received).anyMatch(GoldLooted.class::isInstance)
                .anyMatch(EquipmentLooted.class::isInstance);
        assertThat(bystanderConnection.received).noneMatch(GoldLooted.class::isInstance)
                .noneMatch(EquipmentLooted.class::isInstance);
    }

    /**
     * Un 1 naturel rate toujours et un 20 naturel touche toujours (voir
     * {@code DiceRollerTest}) : les tests "coup garanti"/"raté garanti" ci-dessous
     * retentent quelques fois (RNG réel, pas de mock) jusqu'à obtenir le résultat
     * attendu plutôt que de dépendre uniquement de la CA.
     */
    @Test
    void monsterAttackDealsNaturalDiceDamagePlusStrengthModifier() {
        GameMonster attacker = monsterWithStrengthAndDamage(16, "1d6"); // STR 16 => mod +3
        GamePlayer target = player("Cible", -100); // DEX ridicule => CA quasi nulle, coup quasi garanti

        CombatResult result = monsterAttackUntilHit(attacker, target);

        // 1d6 (1-6) + modificateur de FOR (+3) : entre 4 et 9.
        assertThat(result.damage()).isBetween(4, 9);
    }

    @Test
    void monsterAttackMissLeavesTheTargetUntouched() {
        // Une cible neuve à chaque tentative : un coup accidentel (nat 20 malgré la CA
        // énorme) sur une tentative précédente ne doit pas fausser l'assertion.
        for (int i = 0; i < 20; i++) {
            GamePlayer target = player("Cible", 9999); // CA quasi infinie
            GameMonster attacker = monsterWithStrengthAndDamage(10, "1d6");
            CombatResult result = attacker.tryAttack(target);
            if (!result.hit()) {
                assertThat(result.damage()).isZero();
                return;
            }
        }
        throw new AssertionError("no miss happened in 20 attempts despite an impossible armor class");
    }

    private CombatResult monsterAttackUntilHit(GameMonster attacker, GamePlayer target) {
        for (int i = 0; i < 20; i++) {
            CombatResult result = attacker.tryAttack(target);
            if (result.hit() && !result.criticalHit()) {
                return result;
            }
        }
        throw new AssertionError("no hit landed in 20 attempts despite a trivial armor class");
    }

    private GameMonster monsterWithStrengthAndDamage(int strength, String naturalDamageDice) {
        MonsterTemplate template = new MonsterTemplate(UUID.randomUUID(), "Mannequin", "Un mannequin d'entraînement",
                10, TestAttributes.of(strength, 10, 10, 10, 10, 10), null, 0, naturalDamageDice, 0, List.of(), 0);
        GameMonster monster = new GameMonster(UUID.randomUUID(), template.getName(), template.getId(),
                UUID.randomUUID(), template.getAttributes(), template.getMaxHealth());
        monster.attachTemplate(template);
        return monster;
    }

    private GameMonster monster(Map<Attribute, Integer> attributes, Integer naturalArmorClass) {
        return monster(attributes, naturalArmorClass, 0);
    }

    private GameMonster monster(Map<Attribute, Integer> attributes, Integer naturalArmorClass, int xpReward) {
        return monster(attributes, naturalArmorClass, xpReward, 0, List.of());
    }

    private GameMonster monster(Map<Attribute, Integer> attributes, Integer naturalArmorClass, int xpReward,
            int goldReward, List<MonsterTemplate.LootTableEntry> lootTable) {
        MonsterTemplate template = new MonsterTemplate(UUID.randomUUID(), "Gobelin", "Une créature verte", 7,
                attributes, naturalArmorClass, xpReward, "1d4", goldReward, lootTable, 0);
        GameMonster monster = new GameMonster(UUID.randomUUID(), template.getName(), template.getId(),
                UUID.randomUUID(), attributes, template.getMaxHealth());
        monster.attachTemplate(template);
        Room room = new Room(UUID.randomUUID(), "Clairière", "...", null);
        monster.setCurrentRoom(room);
        room.addMonster(monster);
        return monster;
    }

    private GamePlayer player(String name) {
        return player(name, 10);
    }

    private GamePlayer player(String name, int dexterity) {
        return new GamePlayer(UUID.randomUUID(), UUID.randomUUID(), name, UUID.randomUUID(), Gender.MAN, Race.HUMAN,
                CharacterClass.FIGHTER, 1, 10, 10, TestAttributes.of(10, dexterity, 10, 10, 10, 10), 0, 0);
    }

    /**
     * Contrairement à {@link #player}, insère une vraie ligne {@code account}/
     * {@code character} en base — nécessaire pour les tests de butin d'objet :
     * {@code ItemDao.insert} (déclenché par {@code ItemService
     * .onCharacterLootedItem}) porte une contrainte de clé étrangère réelle sur
     * {@code item.character_id}.
     */
    private GamePlayer persistedPlayer(String name) {
        GamePlayer character = player(name);
        accountDao.insert(new Account(character.getAccountId(), "acct-" + UUID.randomUUID(), "hashed-password", null));
        characterDao.insert(character);
        return character;
    }

    private static final class RecordingConnection implements Connection {

        private final List<OutputMessage> received = new ArrayList<>();

        @Override
        public void requestBlocking(OutputMessage message, Consumer<String> handler) {
            // non utilisé par ces tests
        }

        @Override
        public ConnectionState state() {
            return ConnectionState.INGAME;
        }

        @Override
        public void setState(ConnectionState state) {
            // non utilisé par ces tests
        }

        @Override
        public void send(OutputMessage message) {
            received.add(message);
        }

        @Override
        public void close() {
            // non utilisé par ces tests
        }
    }
}
