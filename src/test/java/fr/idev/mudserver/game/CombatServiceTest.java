package fr.idev.mudserver.game;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import fr.idev.mudserver.domain.CharacterClass;
import fr.idev.mudserver.domain.EquipmentSlot;
import fr.idev.mudserver.domain.GameMonster;
import fr.idev.mudserver.domain.GamePlayer;
import fr.idev.mudserver.domain.Item;
import fr.idev.mudserver.domain.ItemTemplate;
import fr.idev.mudserver.domain.ItemType;
import fr.idev.mudserver.domain.MonsterTemplate;
import fr.idev.mudserver.domain.Race;
import fr.idev.mudserver.domain.Room;
import fr.idev.mudserver.domain.TestAttributes;
import fr.idev.mudserver.game.dice.DiceRoller;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Un 1 naturel rate toujours (même contre une CA ridiculement basse) et un 20
 * naturel touche toujours (même contre une CA ridiculement haute) — les tests
 * "coup garanti"/"raté garanti" ci-dessous ne peuvent donc pas être rendus
 * déterministes par la seule CA : ils retentent quelques fois (RNG réel, pas de
 * mock) jusqu'à obtenir le résultat attendu. Probabilité d'échec du test après
 * 20 tentatives : (1/20)^20, négligeable.
 */
class CombatServiceTest {

    private final CombatService combatService = new CombatService(new DiceRoller());

    @Test
    void resolveHitAlwaysMissesOnNaturalOneRegardlessOfTotal() {
        assertThat(CombatService.resolveHit(1, 999, 5)).isFalse();
    }

    @Test
    void resolveHitAlwaysHitsOnNaturalTwentyRegardlessOfTotal() {
        assertThat(CombatService.resolveHit(20, -50, 999)).isTrue();
    }

    @Test
    void resolveHitComparesTotalToArmorClassOtherwise() {
        assertThat(CombatService.resolveHit(10, 15, 15)).isTrue();
        assertThat(CombatService.resolveHit(10, 14, 15)).isFalse();
    }

    @Test
    void attackWithAnEquippedWeaponDealsWeaponDiceDamage() {
        GamePlayer attacker = player(16, 1);
        equipWeapon(attacker, "1d6");
        Room room = new Room(UUID.randomUUID(), "Arène", "...", null);
        attacker.setCurrentRoom(room);
        GameMonster monster = monster(room, 100, -100);

        CombatResult result = attackUntilHit(attacker, monster);

        // 1d6 (1-6) + modificateur de FOR (+3) : entre 4 et 9.
        assertThat(result.damage()).isBetween(4, 9);
        assertThat(result.monsterDefeated()).isFalse();
    }

    @Test
    void attackWithoutAWeaponDealsUnarmedDamage() {
        GamePlayer attacker = player(14, 1);
        Room room = new Room(UUID.randomUUID(), "Arène", "...", null);
        attacker.setCurrentRoom(room);
        GameMonster monster = monster(room, 100, -100);

        CombatResult result = attackUntilHit(attacker, monster);

        // 1 + modificateur de FOR (+2), pas de dé à mains nues.
        assertThat(result.damage()).isEqualTo(3);
    }

    @Test
    void aLethalHitRemovesTheMonsterFromTheRoomAndClearsTheAttackersTarget() {
        GamePlayer attacker = player(16, 1);
        equipWeapon(attacker, "1d6");
        Room room = new Room(UUID.randomUUID(), "Arène", "...", null);
        attacker.setCurrentRoom(room);
        GameMonster monster = monster(room, 1, -100);
        attacker.setTarget(monster);

        CombatResult result = attackUntilHit(attacker, monster);

        assertThat(result.monsterDefeated()).isTrue();
        assertThat(result.remainingHealth()).isZero();
        assertThat(room.getMonsters()).doesNotContain(monster);
        assertThat(attacker.getTarget()).isNull();
    }

    @Test
    void aMissDealsNoDamageAndLeavesTheMonsterUntouched() {
        GamePlayer attacker = player(10, 1);
        Room room = new Room(UUID.randomUUID(), "Arène", "...", null);
        attacker.setCurrentRoom(room);

        // Un monstre neuf à chaque tentative : un coup accidentel (nat 20 malgré
        // l'énorme CA) sur une tentative précédente ne doit pas fausser l'assertion
        // "PV inchangés" de la tentative qui rate enfin.
        for (int i = 0; i < 20; i++) {
            GameMonster monster = monster(room, 100, 9999);
            CombatResult result = combatService.attack(attacker, monster);
            if (!result.hit()) {
                assertThat(result.damage()).isZero();
                assertThat(result.remainingHealth()).isEqualTo(100);
                assertThat(room.getMonsters()).contains(monster);
                return;
            }
        }
        throw new AssertionError("no miss happened in 20 attempts despite an impossible armor class");
    }

    private CombatResult attackUntilHit(GamePlayer attacker, GameMonster monster) {
        for (int i = 0; i < 20; i++) {
            CombatResult result = combatService.attack(attacker, monster);
            if (result.hit()) {
                return result;
            }
        }
        throw new AssertionError("no hit landed in 20 attempts despite a trivial armor class");
    }

    private void equipWeapon(GamePlayer character, String damageDice) {
        ItemTemplate template = new ItemTemplate(UUID.randomUUID(), "Épée", null, ItemType.WEAPON, 3, null, 0,
                damageDice);
        Item item = new Item(UUID.randomUUID(), template.getId(), null, null, EquipmentSlot.WEAPON);
        item.attachTemplate(template);
        character.addItem(item);
    }

    private GamePlayer player(int strength, int level) {
        return new GamePlayer(UUID.randomUUID(), UUID.randomUUID(), "Attaquant", UUID.randomUUID(), Race.HUMAN,
                CharacterClass.FIGHTER, level, 10, 10, TestAttributes.of(strength, 10, 10, 10, 10, 10));
    }

    private GameMonster monster(Room room, int maxHealth, Integer naturalArmorClass) {
        MonsterTemplate template = new MonsterTemplate(UUID.randomUUID(), "Mannequin", "Un mannequin d'entraînement",
                maxHealth, TestAttributes.of(10, 10, 10, 10, 10, 10), naturalArmorClass);
        GameMonster monster = new GameMonster(UUID.randomUUID(), template.getName(), template.getId(), room.getId(),
                template.getAttributes(), maxHealth);
        monster.attachTemplate(template);
        monster.setCurrentRoom(room);
        room.addMonster(monster);
        return monster;
    }
}
