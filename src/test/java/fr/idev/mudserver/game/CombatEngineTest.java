package fr.idev.mudserver.game;

import java.util.ArrayList;
import java.util.List;
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
import fr.idev.mudserver.domain.HexCoordinate;
import fr.idev.mudserver.domain.RoomInstance;
import fr.idev.mudserver.domain.actor.CharacterClass;
import fr.idev.mudserver.domain.actor.CombatEncounter;
import fr.idev.mudserver.domain.actor.GameMonster;
import fr.idev.mudserver.domain.actor.GamePlayer;
import fr.idev.mudserver.domain.actor.Gender;
import fr.idev.mudserver.domain.actor.MonsterTemplate;
import fr.idev.mudserver.domain.actor.Race;
import fr.idev.mudserver.domain.actor.TestAttributes;
import fr.idev.mudserver.domain.actor.event.GamePlayerEnteredCell;
import fr.idev.mudserver.network.Connection;
import fr.idev.mudserver.network.ConnectionState;
import fr.idev.mudserver.network.OutputMessage;
import fr.idev.mudserver.network.message.ingame.MonsterAttackResult;
import fr.idev.mudserver.network.message.ingame.NotYourTurn;
import fr.idev.mudserver.persistence.AccountDao;
import fr.idev.mudserver.persistence.CharacterDao;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contexte Spring requis (pas {@code @Transactional}), sur le modèle de
 * {@code GameMonsterTest}/{@code ItemRaceConditionTest} : plusieurs de ces
 * tests publient {@code CharacterDied}/{@code GamePlayerDied} via le holder
 * statique {@code DomainEventPublisher}, dont les listeners écrivent en DB —
 * ces écritures ne doivent pas partager la connexion/transaction du thread de
 * test. Les modificateurs d'attribut extrêmes utilisés ci-dessous (DEX/FOR très
 * hauts ou très bas) suivent la même convention que {@code GamePlayerTest} :
 * rendre un résultat déterministe sans mocker le RNG, plutôt que retenter
 * indéfiniment.
 */
class CombatEngineTest extends AbstractIntegrationTest {

    @Autowired
    private CombatEngine combatEngine;

    @Autowired
    private RoomService roomService;

    @Autowired
    private AccountDao accountDao;

    @Autowired
    private CharacterDao characterDao;

    @Test
    void openingStrikeResolvesUnconditionallyAndEstablishesInitiativeWhenTheMonsterSurvives() {
        RoomInstance room = new RoomInstance(UUID.randomUUID(), "Arène", "...", null);
        GamePlayer attacker = player("Attaquant", 10, 10, room);
        GameMonster monster = monster(room, 1000, -1000, 10, 10, "1d6");

        combatEngine.attack(attacker, monster);

        assertThat(attacker.isInCombat()).isTrue();
        assertThat(monster.isInCombat()).isTrue();
        assertThat(attacker.getEncounter()).isSameAs(monster.getEncounter());
        assertThat(attacker.getEncounter().isInitiativeRolled()).isTrue();
        assertThat(attacker.getEncounter().participants()).containsExactlyInAnyOrder(attacker, monster);
    }

    @Test
    void openingStrikeThatKillsTheMonsterLeavesNoEncounterBehind() {
        for (int i = 0; i < 20; i++) {
            RoomInstance room = new RoomInstance(UUID.randomUUID(), "Arène", "...", null);
            // FOR 20 => mod +5, dégâts à mains nues garantis (1+5=6, pas de dé) : létal
            // contre 1 PV. CA -1000 : touche sauf sur un 1 naturel (5% par essai).
            GamePlayer attacker = player("Attaquant", 20, 10, room);
            GameMonster monster = monster(room, 1, -1000, 10, 10, "1d6");

            combatEngine.attack(attacker, monster);

            if (monster.getCurrentHealth() == 0) {
                assertThat(attacker.isInCombat()).isFalse();
                assertThat(monster.isInCombat()).isFalse();
                return;
            }
        }
        throw new AssertionError("no kill happened in 20 attempts despite an impossible armor class");
    }

    @Test
    void concurrentAttacksOnAFreshMonsterProduceExactlyOneSharedEncounter() throws Exception {
        int attackers = 20;
        RoomInstance room = new RoomInstance(UUID.randomUUID(), "Arène", "...", null);
        GameMonster monster = monster(room, 100_000, -1000, 10, 10, "1d6");
        List<GamePlayer> players = new ArrayList<>();
        for (int i = 0; i < attackers; i++) {
            players.add(player("Attaquant" + i, 10, 10, room));
        }

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            CyclicBarrier barrier = new CyclicBarrier(attackers);
            List<Future<?>> results = new ArrayList<>();
            for (GamePlayer p : players) {
                results.add(executor.submit(() -> {
                    barrier.await();
                    combatEngine.attack(p, monster);
                    return null;
                }));
            }
            for (Future<?> result : results) {
                result.get();
            }
        }

        assertThat(monster.getEncounter()).isNotNull();
        for (GamePlayer p : players) {
            assertThat(p.getEncounter()).as("%s should share the monster's encounter", p.getName())
                    .isSameAs(monster.getEncounter());
        }
        List<Object> participants = new ArrayList<>(monster.getEncounter().participants());
        assertThat(participants).hasSize(attackers + 1).contains(monster).containsAll(players);
    }

    @Test
    void joiningAnOngoingEncounterInsertsWithoutAFreeAttack() {
        RoomInstance room = new RoomInstance(UUID.randomUUID(), "Arène", "...", null);
        GamePlayer founder = player("Fondateur", 10, 10, room);
        GameMonster monster = monster(room, 1000, -1000, 10, 10, "1d6");
        combatEngine.attack(founder, monster);
        int monsterHealthBeforeJoin = monster.getCurrentHealth();

        // DEX 100 => initiative quasi garantie la plus haute de l'affrontement.
        GamePlayer joiner = player("Rejoignant", 10, 100, room);
        combatEngine.attack(joiner, monster);

        assertThat(joiner.isInCombat()).isTrue();
        assertThat(joiner.getEncounter()).isSameAs(monster.getEncounter());
        assertThat(monster.getEncounter().participants()).contains(joiner);
        assertThat(monster.getCurrentHealth()).as("joining must not deal damage").isEqualTo(monsterHealthBeforeJoin);
    }

    @Test
    void turnLockRejectsAnActionFromSomeoneWhoseTurnItIsNot() {
        RoomInstance room = new RoomInstance(UUID.randomUUID(), "Arène", "...", null);
        // DEX 100 => A gagne quasi systématiquement l'initiative face au monstre (DEX
        // 10).
        GamePlayer a = player("A", 10, 100, room);
        GameMonster monster = monster(room, 1000, -1000, 10, 10, "1d6");
        combatEngine.attack(a, monster);
        assertThat(monster.getEncounter().currentParticipant()).isEqualTo(a);

        GamePlayer b = player("B", 10, 10, room);
        RecordingConnection bConnection = new RecordingConnection();
        b.setConnection(bConnection);
        combatEngine.attack(b, monster); // rejoint, n'agit pas encore
        int monsterHealthBeforeRejectedAttempt = monster.getCurrentHealth();

        combatEngine.attack(b, monster); // tente d'agir hors de son tour

        assertThat(bConnection.received).anyMatch(NotYourTurn.class::isInstance);
        assertThat(monster.getCurrentHealth()).isEqualTo(monsterHealthBeforeRejectedAttempt);
        assertThat(monster.getEncounter().currentParticipant()).isEqualTo(a);
    }

    @Test
    void multipleActionsPerTurnLetThePlayerActAgainBeforeTheTurnAdvances() {
        RoomInstance room = new RoomInstance(UUID.randomUUID(), "Arène", "...", null);
        // DEX 100 => l'attaquant gagne quasi systématiquement l'initiative face au
        // monstre (DEX 10), même convention que les autres tests de cette classe.
        GamePlayer attacker = player("Attaquant", 10, 100, room);
        GameMonster monster = monster(room, 1000, -1000, 10, 10, "1d4");
        attacker.getActionEconomy().setActionsMax(2);
        RecordingConnection attackerConnection = new RecordingConnection();
        attacker.setConnection(attackerConnection);

        // Coup d'ouverture : établit l'initiative, et resolveFromCurrentTurn
        // réinitialise le budget de l'attaquant s'il gagne l'initiative, prenant en
        // compte le setActionsMax(2) fait ci-dessus.
        combatEngine.attack(attacker, monster);
        assertThat(attacker.getEncounter().currentParticipant()).isEqualTo(attacker);
        assertThat(attacker.getActionEconomy().getActionsRemaining()).isEqualTo(2);

        attackerConnection.received.clear();
        combatEngine.attack(attacker, monster); // consomme la 1ère des 2 actions
        assertThat(attacker.getEncounter().currentParticipant())
                .as("le tour ne doit pas avancer tant qu'il reste une action").isEqualTo(attacker);
        assertThat(attackerConnection.received).noneMatch(MonsterAttackResult.class::isInstance);

        combatEngine.attack(attacker, monster); // consomme la dernière action, épuise le budget : cascade
        assertThat(attackerConnection.received).as("épuiser le budget doit déclencher la riposte du monstre")
                .anyMatch(MonsterAttackResult.class::isInstance);
    }

    @Test
    void monsterTurnResetsItsBudgetButStillResolvesExactlyOneAttackRegardlessOfPoolSize() {
        RoomInstance room = new RoomInstance(UUID.randomUUID(), "Arène", "...", null);
        GamePlayer attacker = player("Attaquant", 10, 100, room);
        GameMonster monster = monster(room, 1000, -1000, 10, 10, "1d4");
        monster.getActionEconomy().setActionsMax(3);
        RecordingConnection attackerConnection = new RecordingConnection();
        attacker.setConnection(attackerConnection);

        combatEngine.attack(attacker, monster); // coup d'ouverture, établit l'initiative
        assertThat(attacker.getEncounter().currentParticipant()).isEqualTo(attacker);

        attackerConnection.received.clear();
        combatEngine.attack(attacker, monster); // épuise l'unique action du joueur : cascade dans le tour du monstre

        assertThat(monster.getActionEconomy().getActionsRemaining())
                .as("le reset de début de tour s'applique aussi aux monstres").isEqualTo(3);
        long monsterAttacksReceived = attackerConnection.received.stream().filter(MonsterAttackResult.class::isInstance)
                .count();
        assertThat(monsterAttacksReceived)
                .as("un monstre ne résout qu'une seule attaque par tour pour l'instant, quelle que soit sa réserve")
                .isEqualTo(1);
    }

    @Test
    void cascadeResolvesEveryMonsterTurnBeforeReturningControlToTheLoneSurvivingPlayer() {
        RoomInstance room = new RoomInstance(UUID.randomUUID(), "Arène", "...", null);
        // DEX 100 => le joueur agit systématiquement en premier ; les deux monstres
        // (DEX 10)
        // se partagent donc tout le reste de l'ordre, sans jamais redevenir "le joueur"
        // tant
        // que la cascade n'a pas fait le tour complet.
        GamePlayer player = player("Solo", 10, 100, room, 200);
        GameMonster first = monster(room, 1000, -1000, 10, 10, "1d4");
        RecordingConnection playerConnection = new RecordingConnection();
        player.setConnection(playerConnection);

        combatEngine.attack(player, first);
        GameMonster second = monster(room, 1000, -1000, 10, 10, "1d4");
        combatEngine.attack(player, second); // fusionne le second monstre dans le même affrontement

        playerConnection.received.clear();
        combatEngine.attack(player, first); // tour du joueur : déclenche la cascade des 2 monstres

        assertThat(player.getEncounter().currentParticipant()).isEqualTo(player);
        long monsterAttacksReceived = playerConnection.received.stream().filter(MonsterAttackResult.class::isInstance)
                .count();
        assertThat(monsterAttacksReceived).isEqualTo(2);
    }

    @Test
    void playerDeathMidEncounterRespawnsWithoutEndingTheFightForTheSurvivor() {
        roomService.warmRooms();
        RoomInstance room = new RoomInstance(UUID.randomUUID(), "Arène", "...", null);
        GameMonster monster = monster(room, 1000, 8, 10, 10, "1d4");
        GamePlayer victim = persistedPlayer("Victime", 10, 10, room, 10);
        GamePlayer survivor = persistedPlayer("Survivant", 10, 10, room, 10);

        combatEngine.attack(victim, monster);
        combatEngine.attack(survivor, monster);
        assertThat(victim.getEncounter()).as("both must share the same encounter before the killing blow")
                .isSameAs(survivor.getEncounter());

        // Le coup fatal lui-même est déclenché directement plutôt que simulé via de
        // nombreux tours aléatoires du moteur (cible/critique tirés au sort) : ce test
        // vise
        // spécifiquement la survie de l'affrontement pour les autres participants, pas
        // la
        // mécanique de ciblage du monstre (déjà couverte ailleurs).
        victim.takeDamage(999, monster);

        // La réapparition automatique (CharacterService#onGamePlayerDied) restaure
        // aussitôt
        // les PV au max en réaction au même événement — voir GamePlayerDeathTest pour
        // la
        // vérification dédiée de ce clamp-à-0-puis-restauration.
        assertThat(victim.getCurrentHealth()).isEqualTo(victim.getMaxHealth());
        assertThat(victim.isInCombat()).isFalse();
        assertThat(victim.getCurrentRoom()).isEqualTo(roomService.startingRoom().orElseThrow());
        assertThat(survivor.isInCombat()).isTrue();
        assertThat(survivor.getEncounter().participants()).contains(survivor, monster).doesNotContain(victim);
    }

    @Test
    void aggroWithNoActiveEncounterStartsANewOneWithoutAFreeHit() {
        RoomInstance room = new RoomInstance(UUID.randomUUID(), "Arène", "...", null);
        // DEX 100 => la victime gagne quasi systématiquement l'initiative face au
        // monstre (DEX 10) : si le monstre avait un coup d'ouverture hors ordre (comme
        // startNewEncounter), ses PV auraient déjà baissé ici — la décision de design
        // actée (pas d'embuscade) veut au contraire que rien ne se passe avant que
        // l'ordre d'initiative ne désigne un participant.
        GamePlayer victim = player("Victime", 10, 100, room);
        GameMonster wolf = monster(room, 1000, -1000, 10, 10, "1d6", 5);
        wolf.setPosition(victim.getPosition());

        combatEngine.onGamePlayerEnteredCell(new GamePlayerEnteredCell(victim, victim.getPosition()));

        assertThat(victim.isInCombat()).isTrue();
        assertThat(wolf.isInCombat()).isTrue();
        assertThat(victim.getEncounter()).isSameAs(wolf.getEncounter());
        assertThat(wolf.getEncounter().isInitiativeRolled()).isTrue();
        assertThat(wolf.getEncounter().currentParticipant()).isEqualTo(victim);
        assertThat(wolf.getCurrentHealth()).as("no free hit for the founding monster").isEqualTo(1000);
    }

    @Test
    void aggroJoinsAnAlreadyFightingMonstersEncounter() {
        RoomInstance room = new RoomInstance(UUID.randomUUID(), "Arène", "...", null);
        GamePlayer founder = player("Fondateur", 10, 10, room);
        GameMonster wolf = monster(room, 1000, -1000, 10, 10, "1d6", 5);
        combatEngine.attack(founder, wolf);

        GamePlayer victim = player("Victime", 10, 10, room);
        wolf.setPosition(victim.getPosition());

        combatEngine.onGamePlayerEnteredCell(new GamePlayerEnteredCell(victim, victim.getPosition()));

        assertThat(victim.isInCombat()).isTrue();
        assertThat(victim.getEncounter()).isSameAs(wolf.getEncounter());
        assertThat(wolf.getEncounter().participants()).contains(founder, victim, wolf);
    }

    @Test
    void aggroMergesASecondFreeMonsterInTheSameEncounter() {
        RoomInstance room = new RoomInstance(UUID.randomUUID(), "Arène", "...", null);
        GamePlayer founder = player("Fondateur", 10, 10, room);
        GameMonster wolf = monster(room, 1000, -1000, 10, 10, "1d6", 5);
        combatEngine.attack(founder, wolf);

        GamePlayer victim = player("Victime", 10, 10, room);
        wolf.setPosition(victim.getPosition());
        GameMonster spider = monster(room, 1000, -1000, 10, 10, "1d6", 5);
        spider.setPosition(victim.getPosition());

        combatEngine.onGamePlayerEnteredCell(new GamePlayerEnteredCell(victim, victim.getPosition()));

        assertThat(victim.isInCombat()).isTrue();
        assertThat(spider.isInCombat()).isTrue();
        CombatEncounter encounter = wolf.getEncounter();
        assertThat(victim.getEncounter()).isSameAs(encounter);
        assertThat(spider.getEncounter()).isSameAs(encounter);
        assertThat(encounter.participants()).contains(founder, victim, wolf, spider);
    }

    private GamePlayer player(String name, int strength, int dexterity, RoomInstance room) {
        return player(name, strength, dexterity, room, 1000);
    }

    private GamePlayer player(String name, int strength, int dexterity, RoomInstance room, int hp) {
        GamePlayer character = new GamePlayer(UUID.randomUUID(), UUID.randomUUID(), name, room.getId(), Gender.MAN,
                Race.HUMAN, CharacterClass.FIGHTER, 1, hp, hp, TestAttributes.of(strength, dexterity, 10, 10, 10, 10),
                0, 0);
        room.join(character);
        return character;
    }

    private GamePlayer persistedPlayer(String name, int strength, int dexterity, RoomInstance room, int hp) {
        Account account = new Account(UUID.randomUUID(), name + "-" + UUID.randomUUID(), "hashed-password", null);
        accountDao.insert(account);
        GamePlayer character = new GamePlayer(UUID.randomUUID(), account.getId(), name, room.getId(), Gender.MAN,
                Race.HUMAN, CharacterClass.FIGHTER, 1, hp, hp, TestAttributes.of(strength, dexterity, 10, 10, 10, 10),
                0, 0);
        characterDao.insert(character);
        room.join(character);
        return character;
    }

    private GameMonster monster(RoomInstance room, int maxHealth, Integer naturalArmorClass, int strength,
            int dexterity, String naturalDamageDice) {
        return monster(room, maxHealth, naturalArmorClass, strength, dexterity, naturalDamageDice, 0);
    }

    private GameMonster monster(RoomInstance room, int maxHealth, Integer naturalArmorClass, int strength,
            int dexterity, String naturalDamageDice, int presenceRadius) {
        MonsterTemplate template = new MonsterTemplate(UUID.randomUUID(), "Mannequin " + UUID.randomUUID(),
                "Un mannequin d'entraînement", maxHealth, TestAttributes.of(strength, dexterity, 10, 10, 10, 10),
                naturalArmorClass, 0, naturalDamageDice, 0, List.of(), presenceRadius);
        GameMonster monster = new GameMonster(UUID.randomUUID(), template.getName(), template.getId(), room.getId(),
                template.getAttributes(), maxHealth);
        monster.attachTemplate(template);
        monster.setCurrentRoom(room);
        room.addMonster(monster);
        return monster;
    }

    private static final class RecordingConnection implements Connection {

        private final List<OutputMessage> received = new ArrayList<>();
        private GamePlayer character;

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

        @Override
        public void setCharacter(GamePlayer character) {
            this.character = character;
        }

        @Override
        public GamePlayer character() {
            return character;
        }
    }
}
