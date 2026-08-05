package fr.idev.mudserver.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GameMonsterTest {

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

        assertThat(monster.takeDamage(3)).isFalse();
        assertThat(monster.getCurrentHealth()).isEqualTo(4);

        assertThat(monster.takeDamage(100)).isTrue();
        assertThat(monster.getCurrentHealth()).isZero();
    }

    @Test
    void takeDamageAfterDeathIsANoOpAndNeverReportsAnotherKillingBlow() {
        GameMonster monster = monster(TestAttributes.of(10, 10, 10, 10, 10, 10), null);
        monster.takeDamage(100);

        assertThat(monster.takeDamage(5)).isFalse();
        assertThat(monster.getCurrentHealth()).isZero();
    }

    @Test
    void exactlyOneConcurrentAttackLandsTheKillingBlow() throws Exception {
        int attackers = 20;
        GameMonster monster = monster(TestAttributes.of(10, 10, 10, 10, 10, 10), null);

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            CyclicBarrier barrier = new CyclicBarrier(attackers);
            List<Future<Boolean>> results = new ArrayList<>();
            for (int i = 0; i < attackers; i++) {
                results.add(executor.submit(() -> {
                    barrier.await();
                    return monster.takeDamage(100);
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

    private GameMonster monster(Map<Attribute, Integer> attributes, Integer naturalArmorClass) {
        MonsterTemplate template = new MonsterTemplate(UUID.randomUUID(), "Gobelin", "Une créature verte", 7,
                attributes, naturalArmorClass);
        GameMonster monster = new GameMonster(UUID.randomUUID(), template.getName(), template.getId(),
                UUID.randomUUID(), attributes, template.getMaxHealth());
        monster.attachTemplate(template);
        return monster;
    }
}
