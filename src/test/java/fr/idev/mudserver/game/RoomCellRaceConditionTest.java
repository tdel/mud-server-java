package fr.idev.mudserver.game;

import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import fr.idev.mudserver.AbstractIntegrationTest;
import fr.idev.mudserver.domain.Account;
import fr.idev.mudserver.domain.HexCoordinate;
import fr.idev.mudserver.domain.RoomInstance;
import fr.idev.mudserver.domain.actor.CharacterClass;
import fr.idev.mudserver.domain.actor.Gender;
import fr.idev.mudserver.domain.actor.GamePlayer;
import fr.idev.mudserver.domain.actor.Race;
import fr.idev.mudserver.domain.actor.TestAttributes;
import fr.idev.mudserver.persistence.AccountDao;
import fr.idev.mudserver.persistence.CharacterDao;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Analogue de {@code game.ItemRaceConditionTest} pour la réclamation de case :
 * deux virtual threads, synchronisés par un {@link CyclicBarrier}, appellent
 * {@link RoomInstance#tryClaimCell} sur la même case au plus près l'un de
 * l'autre. Objectif : prouver que {@code putIfAbsent} sur la
 * {@code ConcurrentHashMap} d'occupation sérialise réellement deux virtual
 * threads concurrents — un seul gagnant possible, jamais deux personnages sur
 * la même case.
 */
class RoomCellRaceConditionTest extends AbstractIntegrationTest {

    private static final int ITERATIONS = 50;

    @Autowired
    private RoomService roomService;

    @Autowired
    private AccountDao accountDao;

    @Autowired
    private CharacterDao characterDao;

    @Test
    void exactlyOneCharacterWinsTheCellClaim() throws Exception {
        roomService.warmRooms();
        RoomInstance room = roomService.allRooms().iterator().next();

        GamePlayer alice = seedCharacter(room, "cell-race-alice-" + UUID.randomUUID());
        GamePlayer bob = seedCharacter(room, "cell-race-bob-" + UUID.randomUUID());

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < ITERATIONS; i++) {
                HexCoordinate contested = new HexCoordinate(i % room.getWidth(), 0);

                CyclicBarrier barrier = new CyclicBarrier(2);
                Callable<Boolean> aliceAttempt = () -> {
                    barrier.await();
                    return room.tryClaimCell(contested, alice);
                };
                Callable<Boolean> bobAttempt = () -> {
                    barrier.await();
                    return room.tryClaimCell(contested, bob);
                };

                Future<Boolean> aliceResult = executor.submit(aliceAttempt);
                Future<Boolean> bobResult = executor.submit(bobAttempt);

                boolean aliceWon = aliceResult.get();
                boolean bobWon = bobResult.get();

                assertThat(aliceWon ^ bobWon)
                        .as("exactement un gagnant à l'itération %d (alice=%s, bob=%s)", i, aliceWon, bobWon).isTrue();
                assertThat(room.occupantAt(contested)).contains(aliceWon ? alice : bob);

                room.releaseCell(contested, aliceWon ? alice : bob);
            }
        }
    }

    private GamePlayer seedCharacter(RoomInstance room, String login) {
        Account account = new Account(UUID.randomUUID(), login, "hashed-password", null);
        accountDao.insert(account);
        GamePlayer character = new GamePlayer(UUID.randomUUID(), account.getId(), login, room.getId(), Gender.MAN,
                Race.HUMAN, CharacterClass.FIGHTER, 1, 10, 10, TestAttributes.of(10, 10, 10, 10, 10, 10), 0, 0);
        characterDao.insert(character);
        return character;
    }
}
