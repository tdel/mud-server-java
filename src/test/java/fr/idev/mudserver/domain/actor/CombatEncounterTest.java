package fr.idev.mudserver.domain.actor;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import fr.idev.mudserver.domain.Account;
import fr.idev.mudserver.domain.RoomInstance;
import fr.idev.mudserver.domain.TestRooms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

/**
 * Unitaire pur (pas de contexte Spring, pas de jet de dés réel) : les tirages
 * d'initiative sont fournis directement (constantes ou fonction de tirage
 * déterministe), voir la Javadoc de {@link CombatEncounter} pour le contrat
 * testé ici — en particulier l'arithmétique du pointeur de tour lors d'une
 * insertion/d'un retrait.
 */
class CombatEncounterTest {

    private final RoomInstance room = TestRooms.room(UUID.randomUUID(), "Arène", "...");

    @Test
    void establishInitiativeOrderSortsDescendingByRoll() {
        CombatEncounter encounter = new CombatEncounter(room);
        GamePlayer a = player(10);
        GameMonster b = monster(10);
        GamePlayer c = player(10);
        encounter.joinBeforeInitiative(a);
        encounter.joinBeforeInitiative(b);
        encounter.joinBeforeInitiative(c);

        Map<GameCharacter, Integer> rolls = Map.of(a, 5, b, 20, c, 12);
        encounter.establishInitiativeOrder(rolls::get);

        assertThat(encounter.participants()).containsExactly(b, c, a);
        assertThat(encounter.currentParticipant()).isEqualTo(b);
    }

    @Test
    void establishInitiativeOrderBreaksTiesByHigherDexModifier() {
        CombatEncounter encounter = new CombatEncounter(room);
        GamePlayer lowDex = player(10);
        GamePlayer highDex = player(18);
        encounter.joinBeforeInitiative(lowDex);
        encounter.joinBeforeInitiative(highDex);

        encounter.establishInitiativeOrder(character -> 12);

        assertThat(encounter.participants()).containsExactly(highDex, lowDex);
    }

    @Test
    void joinBeforeInitiativeThrowsOnceInitiativeIsRolled() {
        CombatEncounter encounter = rolledEncounter(player(10), monster(10));

        assertThatIllegalStateException().isThrownBy(() -> encounter.joinBeforeInitiative(player(10)));
    }

    @Test
    void insertLatecomerBeforePointerIncrementsPointer() {
        GamePlayer a = player(10);
        GameMonster b = monster(10);
        GamePlayer c = player(10);
        // A=20, B=10, C=5 (décroissant) ; pointeur sur B (index 1) après un
        // advanceTurn().
        CombatEncounter encounter = rolledEncounter(Map.of(a, 20, b, 10, c, 5), a, b, c);
        encounter.advanceTurn();
        assertThat(encounter.currentParticipant()).isEqualTo(b);

        GamePlayer newcomer = player(10); // initiative 25 : s'insère avant tout le monde (index 0).
        encounter.insertLatecomer(newcomer, 25);

        assertThat(encounter.participants()).containsExactly(newcomer, a, b, c);
        assertThat(encounter.currentParticipant()).isEqualTo(b);
    }

    @Test
    void insertLatecomerAfterPointerLeavesPointerUnchanged() {
        GamePlayer a = player(10);
        GameMonster b = monster(10);
        GamePlayer c = player(10);
        CombatEncounter encounter = rolledEncounter(Map.of(a, 20, b, 10, c, 5), a, b, c);
        // Pointeur sur A (index 0), inchangé.
        assertThat(encounter.currentParticipant()).isEqualTo(a);

        GamePlayer newcomer = player(10); // initiative 1 : s'insère en dernier (après le pointeur).
        encounter.insertLatecomer(newcomer, 1);

        assertThat(encounter.participants()).containsExactly(a, b, c, newcomer);
        assertThat(encounter.currentParticipant()).isEqualTo(a);
    }

    @Test
    void removeBeforePointerDecrementsPointer() {
        GamePlayer a = player(10);
        GameMonster b = monster(10);
        GamePlayer c = player(10);
        CombatEncounter encounter = rolledEncounter(Map.of(a, 20, b, 10, c, 5), a, b, c);
        encounter.advanceTurn();
        assertThat(encounter.currentParticipant()).isEqualTo(b);

        encounter.remove(a);

        assertThat(encounter.participants()).containsExactly(b, c);
        assertThat(encounter.currentParticipant()).isEqualTo(b);
    }

    @Test
    void removeAtPointerNotLastSlidesNextParticipantIntoTheSameIndex() {
        GamePlayer a = player(10);
        GameMonster b = monster(10);
        GamePlayer c = player(10);
        CombatEncounter encounter = rolledEncounter(Map.of(a, 20, b, 10, c, 5), a, b, c);
        encounter.advanceTurn();
        assertThat(encounter.currentParticipant()).isEqualTo(b);

        encounter.remove(b);

        assertThat(encounter.participants()).containsExactly(a, c);
        assertThat(encounter.currentParticipant()).isEqualTo(c);
    }

    @Test
    void removeAtPointerWhenLastElementWrapsToStart() {
        GamePlayer a = player(10);
        GameMonster b = monster(10);
        GamePlayer c = player(10);
        CombatEncounter encounter = rolledEncounter(Map.of(a, 20, b, 10, c, 5), a, b, c);
        encounter.advanceTurn();
        encounter.advanceTurn();
        assertThat(encounter.currentParticipant()).isEqualTo(c);

        encounter.remove(c);

        assertThat(encounter.participants()).containsExactly(a, b);
        assertThat(encounter.currentParticipant()).isEqualTo(a);
    }

    @Test
    void removeAfterPointerLeavesPointerUnchanged() {
        GamePlayer a = player(10);
        GameMonster b = monster(10);
        GamePlayer c = player(10);
        CombatEncounter encounter = rolledEncounter(Map.of(a, 20, b, 10, c, 5), a, b, c);
        assertThat(encounter.currentParticipant()).isEqualTo(a);

        encounter.remove(c);

        assertThat(encounter.participants()).containsExactly(a, b);
        assertThat(encounter.currentParticipant()).isEqualTo(a);
    }

    @Test
    void isOverIsFalseBeforeInitiativeIsRolled() {
        CombatEncounter encounter = new CombatEncounter(room);
        encounter.joinBeforeInitiative(player(10));

        assertThat(encounter.isOver()).isFalse();
    }

    @Test
    void isOverIsTrueWhenNoMonsterRemains() {
        CombatEncounter encounter = rolledEncounter(player(10), player(10));

        assertThat(encounter.isOver()).isTrue();
    }

    @Test
    void isOverIsTrueWhenNoPlayerRemains() {
        CombatEncounter encounter = rolledEncounter(monster(10), monster(10));

        assertThat(encounter.isOver()).isTrue();
    }

    @Test
    void isOverIsFalseWhenBothSidesRemain() {
        CombatEncounter encounter = rolledEncounter(player(10), monster(10));

        assertThat(encounter.isOver()).isFalse();
    }

    private CombatEncounter rolledEncounter(GameCharacter... characters) {
        CombatEncounter encounter = new CombatEncounter(room);
        for (GameCharacter character : characters) {
            encounter.joinBeforeInitiative(character);
        }
        encounter.establishInitiativeOrder(character -> 10);
        return encounter;
    }

    private CombatEncounter rolledEncounter(Map<GameCharacter, Integer> rolls, GameCharacter... characters) {
        CombatEncounter encounter = new CombatEncounter(room);
        for (GameCharacter character : characters) {
            encounter.joinBeforeInitiative(character);
        }
        encounter.establishInitiativeOrder(rolls::get);
        return encounter;
    }

    private GamePlayer player(int dexterity) {
        Account account = new Account(UUID.randomUUID(), "player-" + UUID.randomUUID(), "hashed-password", null);
        return new GamePlayer(UUID.randomUUID(), account, "Joueur", room, Gender.MAN, Race.HUMAN,
                CharacterClass.FIGHTER, 1, 10, 10, TestAttributes.of(10, dexterity, 10, 10, 10, 10), 0, 0);
    }

    private GameMonster monster(int dexterity) {
        MonsterTemplate template = new MonsterTemplate(UUID.randomUUID(), "Mannequin", "...", 10,
                TestAttributes.of(10, dexterity, 10, 10, 10, 10), null, 0, "1d4", 0, List.of(), 0);
        GameMonster monster = new GameMonster(UUID.randomUUID(), template.getName(), template.getId(), room.getId(),
                template.getAttributes(), 10);
        monster.attachTemplate(template);
        monster.setCurrentRoom(room);
        return monster;
    }
}
