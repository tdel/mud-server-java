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

import fr.idev.mudserver.AbstractIntegrationTest;
import fr.idev.mudserver.domain.Room;
import fr.idev.mudserver.network.Connection;
import fr.idev.mudserver.network.ConnectionState;
import fr.idev.mudserver.network.OutputMessage;
import fr.idev.mudserver.network.message.ingame.MonsterDefeated;

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

    private GameMonster monster(Map<Attribute, Integer> attributes, Integer naturalArmorClass) {
        return monster(attributes, naturalArmorClass, 0);
    }

    private GameMonster monster(Map<Attribute, Integer> attributes, Integer naturalArmorClass, int xpReward) {
        MonsterTemplate template = new MonsterTemplate(UUID.randomUUID(), "Gobelin", "Une créature verte", 7,
                attributes, naturalArmorClass, xpReward, "1d4");
        GameMonster monster = new GameMonster(UUID.randomUUID(), template.getName(), template.getId(),
                UUID.randomUUID(), attributes, template.getMaxHealth());
        monster.attachTemplate(template);
        Room room = new Room(UUID.randomUUID(), "Clairière", "...", null);
        monster.setCurrentRoom(room);
        room.addMonster(monster);
        return monster;
    }

    private GamePlayer player(String name) {
        return new GamePlayer(UUID.randomUUID(), UUID.randomUUID(), name, UUID.randomUUID(), Gender.MAN, Race.HUMAN,
                CharacterClass.FIGHTER, 1, 10, 10, TestAttributes.of(10, 10, 10, 10, 10, 10), 0);
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
