package fr.idev.mudserver.game;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import fr.idev.mudserver.domain.actor.CharacterClass;
import fr.idev.mudserver.domain.ArmorCategory;
import fr.idev.mudserver.domain.EquipmentSlot;
import fr.idev.mudserver.domain.actor.GameMonster;
import fr.idev.mudserver.domain.actor.GamePlayer;
import fr.idev.mudserver.domain.actor.Gender;
import fr.idev.mudserver.domain.Item;
import fr.idev.mudserver.domain.ItemTemplate;
import fr.idev.mudserver.domain.ItemType;
import fr.idev.mudserver.domain.actor.MonsterTemplate;
import fr.idev.mudserver.domain.actor.Race;
import fr.idev.mudserver.domain.Room;
import fr.idev.mudserver.domain.actor.TestAttributes;
import fr.idev.mudserver.domain.actor.TestProficiencies;
import fr.idev.mudserver.game.dice.DiceRoller;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Un 1 naturel rate toujours (même contre une CA ridiculement basse) et un 20
 * naturel touche toujours (même contre une CA ridiculement haute) — les tests
 * "coup garanti"/"raté garanti" ci-dessous ne peuvent donc pas être rendus
 * déterministes par la seule CA : ils retentent quelques fois (RNG réel, pas de
 * mock) jusqu'à obtenir le résultat attendu. Probabilité d'échec du test après
 * 20 tentatives : (1/20)^20, négligeable.
 *
 * <p>
 * Test unitaire pur (pas de contexte Spring) : {@code tryAttack} ne fait plus
 * que résoudre le jet d'attaque et le jet de dégâts, sans jamais appliquer les
 * dégâts ni publier d'événement (voir sa Javadoc) — l'application des dégâts et
 * la cascade « mort du monstre » sont désormais exercées directement sur
 * {@code GameMonster#takeDamage}, dans {@code GameMonsterTest}.
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
    void aMissDealsNoDamageAndLeavesTheMonsterUntouched() {
        GamePlayer attacker = player(10, 1);
        Room room = new Room(UUID.randomUUID(), "Arène", "...", null);
        attacker.setCurrentRoom(room);

        // Un monstre neuf à chaque tentative : un coup accidentel (nat 20 malgré
        // l'énorme CA) sur une tentative précédente ne doit pas fausser l'assertion
        // "PV inchangés" de la tentative qui rate enfin.
        for (int i = 0; i < 20; i++) {
            GameMonster monster = monster(room, 100, 9999);
            CombatResult result = combatService.tryAttack(attacker, monster);
            if (!result.hit()) {
                assertThat(result.damage()).isZero();
                assertThat(monster.getCurrentHealth()).isEqualTo(100);
                assertThat(room.getMonsters()).contains(monster);
                return;
            }
        }
        throw new AssertionError("no miss happened in 20 attempts despite an impossible armor class");
    }

    @Test
    void monsterAttackDealsNaturalDiceDamagePlusStrengthModifier() {
        Room room = new Room(UUID.randomUUID(), "Arène", "...", null);
        GameMonster attacker = monster(room, 10, null, 16, "1d6"); // STR 16 => mod +3
        GamePlayer target = player(10, -100, 1); // DEX ridicule => CA quasi nulle, coup quasi garanti
        target.setCurrentRoom(room);

        CombatResult result = monsterAttackUntilHit(attacker, target);

        // 1d6 (1-6) + modificateur de FOR (+3) : entre 4 et 9.
        assertThat(result.damage()).isBetween(4, 9);
    }

    @Test
    void monsterAttackMissLeavesTheTargetUntouched() {
        Room room = new Room(UUID.randomUUID(), "Arène", "...", null);
        GamePlayer target = player(10, 10, 1);

        // Un attaquant neuf à chaque tentative : un coup accidentel (nat 20 malgré la
        // CA
        // énorme) sur une tentative précédente ne doit pas fausser l'assertion.
        for (int i = 0; i < 20; i++) {
            equipArmor(target, 9999);
            GameMonster attacker = monster(room, 10, null, 10, "1d6");
            CombatResult result = combatService.tryMonsterAttack(attacker, target);
            if (!result.hit()) {
                assertThat(result.damage()).isZero();
                return;
            }
        }
        throw new AssertionError("no miss happened in 20 attempts despite an impossible armor class");
    }

    @Test
    void rollInitiativeIsWithinTheExpectedRangeForTheDexterityModifier() {
        GamePlayer character = player(10, 18, 1); // DEX 18 => modificateur +4

        for (int i = 0; i < 50; i++) {
            assertThat(combatService.rollInitiative(character)).isBetween(1 + 4, 20 + 4);
        }
    }

    /**
     * Exclut délibérément les critiques (double les dés de dégâts) : les tests
     * appelants vérifient une plage de dégâts précise pour un coup normal, qu'un
     * critique élargirait.
     */
    private CombatResult attackUntilHit(GamePlayer attacker, GameMonster monster) {
        for (int i = 0; i < 20; i++) {
            CombatResult result = combatService.tryAttack(attacker, monster);
            if (result.hit() && !result.criticalHit()) {
                return result;
            }
        }
        throw new AssertionError("no non-critical hit landed in 20 attempts despite a trivial armor class");
    }

    private CombatResult monsterAttackUntilHit(GameMonster attacker, GamePlayer target) {
        for (int i = 0; i < 20; i++) {
            CombatResult result = combatService.tryMonsterAttack(attacker, target);
            if (result.hit() && !result.criticalHit()) {
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
        character.getInventory().addItem(item);
    }

    private void equipArmor(GamePlayer character, int baseAc) {
        ItemTemplate template = new ItemTemplate(UUID.randomUUID(), "Armure", null, ItemType.ARMOR, 10,
                ArmorCategory.HEAVY, baseAc, null);
        Item item = new Item(UUID.randomUUID(), template.getId(), null, null, EquipmentSlot.CHEST);
        item.attachTemplate(template);
        character.getInventory().addItem(item);
    }

    private GamePlayer player(int strength, int level) {
        return player(strength, 10, level);
    }

    private GamePlayer player(int strength, int dexterity, int level) {
        return new GamePlayer(UUID.randomUUID(), UUID.randomUUID(), "Attaquant", UUID.randomUUID(), Gender.MAN,
                Race.HUMAN, CharacterClass.FIGHTER, TestProficiencies.savingThrows(CharacterClass.FIGHTER),
                TestProficiencies.skills(CharacterClass.FIGHTER), level, 10, 10,
                TestAttributes.of(strength, dexterity, 10, 10, 10, 10), 0, 0);
    }

    private GameMonster monster(Room room, int maxHealth, Integer naturalArmorClass) {
        return monster(room, maxHealth, naturalArmorClass, 10, "1d6");
    }

    private GameMonster monster(Room room, int maxHealth, Integer naturalArmorClass, int strength,
            String naturalDamageDice) {
        MonsterTemplate template = new MonsterTemplate(UUID.randomUUID(), "Mannequin", "Un mannequin d'entraînement",
                maxHealth, TestAttributes.of(strength, 10, 10, 10, 10, 10), naturalArmorClass, 0, naturalDamageDice);
        GameMonster monster = new GameMonster(UUID.randomUUID(), template.getName(), template.getId(), room.getId(),
                template.getAttributes(), maxHealth);
        monster.attachTemplate(template);
        monster.setCurrentRoom(room);
        room.addMonster(monster);
        return monster;
    }
}
