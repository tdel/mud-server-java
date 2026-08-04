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
import fr.idev.mudserver.domain.Character;
import fr.idev.mudserver.domain.Item;
import fr.idev.mudserver.domain.ItemTemplate;
import fr.idev.mudserver.domain.ItemType;
import fr.idev.mudserver.domain.Race;
import fr.idev.mudserver.domain.Room;
import fr.idev.mudserver.domain.TestAttributes;
import fr.idev.mudserver.persistence.AccountDao;
import fr.idev.mudserver.persistence.CharacterDao;
import fr.idev.mudserver.persistence.ItemDao;
import fr.idev.mudserver.persistence.ItemTemplateDao;
import fr.idev.mudserver.persistence.RoomDao;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrairement au test PHP équivalent ({@code ItemRaceConditionTest}, deux
 * appels {@code dispatch()} séquentiels sur le même thread — pas une vraie
 * race), ce test induit une concurrence réelle : deux virtual threads,
 * synchronisés par un {@link CyclicBarrier} pour arriver au plus près l'un de
 * l'autre, appellent {@link Character#pickUpItem} sur le même item. Objectif :
 * prouver que le {@code synchronized(item)} de {@code pickUpItem} sérialise
 * réellement deux virtual threads concurrents (voir historique : ce test
 * couvrait auparavant un verrou DB {@code SELECT ... FOR UPDATE}, remplacé par
 * ce verrou JVM puisque la gestion des items est désormais entièrement en
 * mémoire). Volontairement pas {@code @Transactional} au niveau du test — les
 * écritures DB déclenchées par l'événement {@code ItemPickedUp} ne doivent pas
 * partager la connexion/transaction du thread de test.
 */
class ItemRaceConditionTest extends AbstractIntegrationTest {

    private static final int ITERATIONS = 50;

    @Autowired
    private ItemDao itemDao;

    @Autowired
    private RoomDao roomDao;

    @Autowired
    private AccountDao accountDao;

    @Autowired
    private CharacterDao characterDao;

    @Autowired
    private ItemTemplateDao itemTemplateDao;

    @Autowired
    private RoomService roomService;

    @Test
    void exactlyOneCharacterWinsTheRace() throws Exception {
        Room room = new Room(UUID.randomUUID(), "Race test room", "...", null);
        roomDao.insert(room);
        roomService.warmRooms();

        Character alice = seedCharacter(room, "race-alice-" + UUID.randomUUID());
        Character bob = seedCharacter(room, "race-bob-" + UUID.randomUUID());

        ItemTemplate template = new ItemTemplate(UUID.randomUUID(), "race-item-" + UUID.randomUUID(), null,
                ItemType.MISC, 1);
        itemTemplateDao.insert(template);

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < ITERATIONS; i++) {
                Item item = new Item(UUID.randomUUID(), template.getId(), room.getId(), null, null);
                itemDao.insert(item);

                CyclicBarrier barrier = new CyclicBarrier(2);
                Callable<Boolean> aliceAttempt = () -> {
                    barrier.await();
                    return alice.pickUpItem(item);
                };
                Callable<Boolean> bobAttempt = () -> {
                    barrier.await();
                    return bob.pickUpItem(item);
                };

                Future<Boolean> aliceResult = executor.submit(aliceAttempt);
                Future<Boolean> bobResult = executor.submit(bobAttempt);

                boolean aliceWon = aliceResult.get();
                boolean bobWon = bobResult.get();

                assertThat(aliceWon ^ bobWon)
                        .as("exactement un gagnant à l'itération %d (alice=%s, bob=%s)", i, aliceWon, bobWon).isTrue();

                Item afterRace = itemDao.findById(item.getId()).orElseThrow();
                UUID winnerId = aliceWon ? alice.getId() : bob.getId();
                assertThat(afterRace.getCharacterId()).isEqualTo(winnerId);
            }
        }
    }

    private Character seedCharacter(Room room, String login) {
        Account account = new Account(UUID.randomUUID(), login, "hashed-password", null);
        accountDao.insert(account);
        Character character = new Character(UUID.randomUUID(), account.getId(), login, room.getId(), Race.HUMAN, 1, 10,
                10, TestAttributes.of(10, 10, 10, 10, 10, 10));
        characterDao.insert(character);
        room(room.getId()).join(character);
        return character;
    }

    private Room room(UUID roomId) {
        return roomService.allRooms().stream().filter(room -> room.getId().equals(roomId)).findFirst().orElseThrow();
    }
}
