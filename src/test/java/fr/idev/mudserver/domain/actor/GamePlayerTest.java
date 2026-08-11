package fr.idev.mudserver.domain.actor;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import fr.idev.mudserver.domain.ArmorCategory;
import fr.idev.mudserver.domain.EquipmentSlot;
import fr.idev.mudserver.domain.Item;
import fr.idev.mudserver.domain.ItemTemplate;
import fr.idev.mudserver.domain.ItemType;
import fr.idev.mudserver.domain.Rarity;
import fr.idev.mudserver.domain.RoomInstance;
import fr.idev.mudserver.domain.TestRooms;
import fr.idev.mudserver.domain.WeaponCategory;
import fr.idev.mudserver.game.CombatResult;
import fr.idev.mudserver.game.dice.CheckResult;

import static org.assertj.core.api.Assertions.assertThat;

class GamePlayerTest {

    @Test
    void modifierRoundsDownForOddScores() {
        GamePlayer character = character(7, 10, 12, 20, 10, 10, 1);

        assertThat(character.getModifier(Attribute.STRENGTH)).isEqualTo(-2);
        assertThat(character.getModifier(Attribute.DEXTERITY)).isEqualTo(0);
        assertThat(character.getModifier(Attribute.CONSTITUTION)).isEqualTo(1);
        assertThat(character.getModifier(Attribute.INTELLIGENCE)).isEqualTo(5);
    }

    @Test
    void proficiencyBonusFollowsTheLevelTiers() {
        assertThat(character(10, 10, 10, 10, 10, 10, 1).getProficiencyBonus()).isEqualTo(2);
        assertThat(character(10, 10, 10, 10, 10, 10, 4).getProficiencyBonus()).isEqualTo(2);
        assertThat(character(10, 10, 10, 10, 10, 10, 5).getProficiencyBonus()).isEqualTo(3);
        assertThat(character(10, 10, 10, 10, 10, 10, 8).getProficiencyBonus()).isEqualTo(3);
        assertThat(character(10, 10, 10, 10, 10, 10, 9).getProficiencyBonus()).isEqualTo(4);
        assertThat(character(10, 10, 10, 10, 10, 10, 20).getProficiencyBonus()).isEqualTo(6);
    }

    @Test
    void armorClassWithNoChestArmorFallsBackToTenPlusDexModifier() {
        GamePlayer character = character(10, 16, 10, 10, 10, 10, 1);

        assertThat(character.getArmorClass()).isEqualTo(13);
    }

    @Test
    void armorClassWithLightArmorAddsTheFullDexModifier() {
        GamePlayer character = character(10, 16, 10, 10, 10, 10, 1);
        equip(character, armor("Cuir", ArmorCategory.LIGHT, 11));

        assertThat(character.getArmorClass()).isEqualTo(14);
    }

    @Test
    void armorClassWithMediumArmorCapsTheDexModifierAtTwo() {
        GamePlayer character = character(10, 18, 10, 10, 10, 10, 1);
        equip(character, armor("Cotte de mailles", ArmorCategory.MEDIUM, 13));

        assertThat(character.getArmorClass()).isEqualTo(15);
    }

    @Test
    void armorClassWithHeavyArmorIgnoresTheDexModifierEntirely() {
        GamePlayer character = character(10, 18, 10, 10, 10, 10, 1);
        equip(character, armor("Plates", ArmorCategory.HEAVY, 18));

        assertThat(character.getArmorClass()).isEqualTo(18);
    }

    @Test
    void armorClassAddsTheShieldBonusOnTopOfBodyArmor() {
        GamePlayer character = character(10, 14, 10, 10, 10, 10, 1);
        equip(character, armor("Cuir", ArmorCategory.LIGHT, 11));
        equip(character, shield(0));

        assertThat(character.getArmorClass()).isEqualTo(15);
    }

    @Test
    void armorClassAddsTheMagicBonusOfEquippedArmorAndShield() {
        GamePlayer character = character(10, 14, 10, 10, 10, 10, 1);
        equip(character, armor("Plates +2", ArmorCategory.HEAVY, 18, 2));
        equip(character, shield(1));

        // 18 (base) + 2 (bonus armure) + 2 (bouclier) + 1 (bonus bouclier).
        assertThat(character.getArmorClass()).isEqualTo(23);
    }

    @Test
    void isWearingNonProficientArmorIsFalseWithNothingEquipped() {
        GamePlayer character = character(10, 10, 10, 10, 10, 10, 1, CharacterClass.WIZARD);

        assertThat(character.isWearingNonProficientArmor()).isFalse();
    }

    @Test
    void isWearingNonProficientArmorIsFalseWhenTheArmorCategoryIsProficient() {
        // FIGHTER maîtrise l'armure lourde.
        GamePlayer character = character(10, 10, 10, 10, 10, 10, 1, CharacterClass.FIGHTER);
        equip(character, armor("Plates", ArmorCategory.HEAVY, 18));

        assertThat(character.isWearingNonProficientArmor()).isFalse();
    }

    @Test
    void isWearingNonProficientArmorIsTrueWhenTheArmorCategoryIsNotProficient() {
        // WIZARD ne maîtrise aucune armure.
        GamePlayer character = character(10, 10, 10, 10, 10, 10, 1, CharacterClass.WIZARD);
        equip(character, armor("Cuir", ArmorCategory.LIGHT, 11));

        assertThat(character.isWearingNonProficientArmor()).isTrue();
    }

    @Test
    void isWearingNonProficientArmorIsTrueForANonProficientShield() {
        // Un bouclier n'a pas d'ArmorCategory (voir data/items.json) : sa maîtrise se
        // dérive du type d'item, jamais de la catégorie d'armure.
        GamePlayer character = character(10, 10, 10, 10, 10, 10, 1, CharacterClass.WIZARD);
        equip(character, shield(0));

        assertThat(character.isWearingNonProficientArmor()).isTrue();
    }

    @Test
    void checkAppliesProficiencyBonusWhenTheClassIsProficientInTheSkill() {
        // FIGHTER est proficient en ATHLETICS (voir data/class.json) : mod FOR +3,
        // bonus de maîtrise niveau 1 = +2.
        GamePlayer fighter = character(16, 10, 10, 10, 10, 10, 1, CharacterClass.FIGHTER);

        for (int i = 0; i < 50; i++) {
            CheckResult result = fighter.check(Skill.ATHLETICS, 0);
            assertThat(result.proficient()).isTrue();
            assertThat(result.total()).isBetween(1 + 3 + 2, 20 + 3 + 2);
        }
    }

    @Test
    void checkDoesNotApplyProficiencyBonusWhenTheClassIsNotProficientInTheSkill() {
        // FIGHTER n'est pas proficient en STEALTH.
        GamePlayer fighter = character(10, 10, 10, 10, 10, 10, 1, CharacterClass.FIGHTER);

        for (int i = 0; i < 50; i++) {
            CheckResult result = fighter.check(Skill.STEALTH, 0);
            assertThat(result.proficient()).isFalse();
            assertThat(result.total()).isBetween(1, 20);
        }
    }

    @Test
    void saveAppliesProficiencyBonusWhenTheClassIsProficientInTheSavingThrow() {
        // FIGHTER est proficient en jets de sauvegarde de FOR.
        GamePlayer fighter = character(16, 10, 10, 10, 10, 10, 1, CharacterClass.FIGHTER);

        for (int i = 0; i < 50; i++) {
            CheckResult result = fighter.save(Attribute.STRENGTH, 0);
            assertThat(result.proficient()).isTrue();
            assertThat(result.total()).isBetween(1 + 3 + 2, 20 + 3 + 2);
        }
    }

    @Test
    void saveDoesNotApplyProficiencyBonusWhenTheClassIsNotProficientInTheSavingThrow() {
        // FIGHTER n'est pas proficient en jets de sauvegarde d'INT.
        GamePlayer fighter = character(10, 10, 10, 10, 10, 10, 1, CharacterClass.FIGHTER);

        for (int i = 0; i < 50; i++) {
            CheckResult result = fighter.save(Attribute.INTELLIGENCE, 0);
            assertThat(result.proficient()).isFalse();
            assertThat(result.total()).isBetween(1, 20);
        }
    }

    @Test
    void aTrivialDcAlwaysSucceeds() {
        GamePlayer fighter = character(10, 10, 10, 10, 10, 10, 1, CharacterClass.FIGHTER);

        assertThat(fighter.check(Skill.ATHLETICS, -100).success()).isTrue();
    }

    @Test
    void anImpossibleDcAlwaysFails() {
        GamePlayer fighter = character(10, 10, 10, 10, 10, 10, 1, CharacterClass.FIGHTER);

        assertThat(fighter.check(Skill.ATHLETICS, 9999).success()).isFalse();
    }

    @Test
    void checkAppliesDisadvantageOnADexterityBasedSkillWhenWearingNonProficientArmor() {
        // WIZARD n'a aucune maîtrise d'armure ; DEX 10 => mod 0, pas de maîtrise sur
        // STEALTH non plus.
        GamePlayer wizard = character(10, 10, 10, 10, 10, 10, 1, CharacterClass.WIZARD);
        equip(wizard, armor("Armure", ArmorCategory.LIGHT, 11));

        // Désavantage (2d20 garde le plus bas, moyenne ≈ 6.86) : nettement sous la
        // moyenne sans désavantage (10.5).
        double average = averageCheckTotal(wizard, Skill.STEALTH, 2000);
        assertThat(average).isBetween(5.8, 7.9);
    }

    @Test
    void checkDoesNotApplyDisadvantageOnAWisdomBasedSkillRegardlessOfArmor() {
        // PERCEPTION est gouvernée par la SAGESSE, pas la DEX/FOR : le désavantage
        // d'armure ne doit pas s'appliquer.
        GamePlayer wizard = character(10, 10, 10, 10, 10, 10, 1, CharacterClass.WIZARD);
        equip(wizard, armor("Armure", ArmorCategory.LIGHT, 11));

        double average = averageCheckTotal(wizard, Skill.PERCEPTION, 2000);
        assertThat(average).isBetween(9.5, 11.5);
    }

    @Test
    void saveAppliesDisadvantageOnADexteritySavingThrowWhenWearingNonProficientArmor() {
        GamePlayer wizard = character(10, 10, 10, 10, 10, 10, 1, CharacterClass.WIZARD);
        equip(wizard, armor("Armure", ArmorCategory.LIGHT, 11));

        double average = averageSaveTotal(wizard, Attribute.DEXTERITY, 2000);
        assertThat(average).isBetween(5.8, 7.9);
    }

    /**
     * Un 1 naturel rate toujours (même contre une CA ridiculement basse) et un 20
     * naturel touche toujours (même contre une CA ridiculement haute) — les tests
     * "coup garanti"/"raté garanti" ci-dessous ne peuvent donc pas être rendus
     * déterministes par la seule CA : ils retentent quelques fois (RNG réel, pas de
     * mock) jusqu'à obtenir le résultat attendu. Probabilité d'échec du test après
     * 20 tentatives : (1/20)^20, négligeable.
     */
    @Test
    void attackWithAnEquippedWeaponDealsWeaponDiceDamage() {
        GamePlayer attacker = character(16, 10, 10, 10, 10, 10, 1);
        equip(attacker, weapon("1d6"));
        RoomInstance room = TestRooms.room(UUID.randomUUID(), "Arène", "...");
        attacker.setCurrentRoom(room);
        GameMonster monster = monster(room, 100, -100);

        CombatResult result = attackUntilHit(attacker, monster);

        // 1d6 (1-6) + modificateur de FOR (+3) : entre 4 et 9.
        assertThat(result.damage()).isBetween(4, 9);
    }

    @Test
    void attackWithAMagicWeaponAddsItsBonusToDamage() {
        GamePlayer attacker = character(16, 10, 10, 10, 10, 10, 1);
        equip(attacker, weapon("1d6", 2));
        RoomInstance room = TestRooms.room(UUID.randomUUID(), "Arène", "...");
        attacker.setCurrentRoom(room);
        GameMonster monster = monster(room, 100, -100);

        CombatResult result = attackUntilHit(attacker, monster);

        // 1d6 (1-6) + modificateur de FOR (+3) + bonus d'arme (+2) : entre 6 et 11.
        assertThat(result.damage()).isBetween(6, 11);
    }

    @Test
    void attackWithoutAWeaponDealsUnarmedDamage() {
        GamePlayer attacker = character(14, 10, 10, 10, 10, 10, 1);
        RoomInstance room = TestRooms.room(UUID.randomUUID(), "Arène", "...");
        attacker.setCurrentRoom(room);
        GameMonster monster = monster(room, 100, -100);

        CombatResult result = attackUntilHit(attacker, monster);

        // 1 + modificateur de FOR (+2), pas de dé à mains nues.
        assertThat(result.damage()).isEqualTo(3);
    }

    @Test
    void aMissDealsNoDamageAndLeavesTheMonsterUntouched() {
        GamePlayer attacker = character(10, 10, 10, 10, 10, 10, 1);
        RoomInstance room = TestRooms.room(UUID.randomUUID(), "Arène", "...");
        attacker.setCurrentRoom(room);

        // Un monstre neuf à chaque tentative : un coup accidentel (nat 20 malgré
        // l'énorme CA) sur une tentative précédente ne doit pas fausser l'assertion
        // "PV inchangés" de la tentative qui rate enfin.
        for (int i = 0; i < 20; i++) {
            GameMonster monster = monster(room, 100, 9999);
            CombatResult result = attacker.tryAttack(monster);
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
    void attackBonusIncludesProficiencyWhenTheWeaponsCategoryMatchesTheClass() {
        GamePlayer attacker = character(10, 10, 10, 10, 10, 10, 1, CharacterClass.FIGHTER); // FIGHTER maîtrise
                                                                                            // MARTIAL
        equip(attacker, weapon("1d6", 0, WeaponCategory.MARTIAL));
        RoomInstance room = TestRooms.room(UUID.randomUUID(), "Arène", "...");
        attacker.setCurrentRoom(room);
        GameMonster monster = monster(room, 100, 9999); // CA impossible : jamais touché, seul le jet compte

        // FOR mod 0 + bonus de maîtrise niveau 1 (+2) : moyenne 1d20 (10.5) + 2 = 12.5.
        double average = averageAttackRoll(attacker, monster, 2000);
        assertThat(average).isBetween(11.5, 13.5);
    }

    @Test
    void attackBonusExcludesProficiencyWhenTheWeaponsCategoryDoesNotMatchTheClass() {
        GamePlayer attacker = character(10, 10, 10, 10, 10, 10, 1, CharacterClass.WIZARD); // WIZARD ne maîtrise que
                                                                                            // SIMPLE
        equip(attacker, weapon("1d6", 0, WeaponCategory.MARTIAL));
        RoomInstance room = TestRooms.room(UUID.randomUUID(), "Arène", "...");
        attacker.setCurrentRoom(room);
        GameMonster monster = monster(room, 100, 9999);

        // FOR mod 0, pas de bonus de maîtrise : moyenne 1d20 (10.5) seule.
        double average = averageAttackRoll(attacker, monster, 2000);
        assertThat(average).isBetween(9.5, 11.5);
    }

    @Test
    void attackRollUsesDisadvantageWhenWearingNonProficientArmor() {
        GamePlayer attacker = character(10, 10, 10, 10, 10, 10, 1, CharacterClass.WIZARD); // aucune maîtrise
                                                                                            // d'armure
        equip(attacker, armor("Armure", ArmorCategory.LIGHT, 10));
        RoomInstance room = TestRooms.room(UUID.randomUUID(), "Arène", "...");
        attacker.setCurrentRoom(room);
        GameMonster monster = monster(room, 100, 9999);

        // À mains nues (toujours "maîtrisé") : FOR mod 0 + bonus de maîtrise (+2), mais
        // désavantage sur le jet de 1d20 (2d20 garde le plus bas, moyenne ≈ 6.86) :
        // moyenne totale ≈ 8.86, nettement sous la moyenne sans désavantage (12.5).
        double average = averageAttackRoll(attacker, monster, 2000);
        assertThat(average).isBetween(7.5, 10.2);
    }

    private double averageAttackRoll(GamePlayer attacker, GameMonster monster, int iterations) {
        long total = 0;
        for (int i = 0; i < iterations; i++) {
            total += attacker.tryAttack(monster).attackRoll();
        }
        return (double) total / iterations;
    }

    /**
     * Exclut délibérément les critiques (double les dés de dégâts) : les tests
     * appelants vérifient une plage de dégâts précise pour un coup normal, qu'un
     * critique élargirait.
     */
    private CombatResult attackUntilHit(GamePlayer attacker, GameMonster monster) {
        for (int i = 0; i < 20; i++) {
            CombatResult result = attacker.tryAttack(monster);
            if (result.hit() && !result.criticalHit()) {
                return result;
            }
        }
        throw new AssertionError("no non-critical hit landed in 20 attempts despite a trivial armor class");
    }

    private GameMonster monster(RoomInstance room, int maxHealth, Integer naturalArmorClass) {
        MonsterTemplate template = new MonsterTemplate(UUID.randomUUID(), "Mannequin", "Un mannequin d'entraînement",
                maxHealth, TestAttributes.of(10, 10, 10, 10, 10, 10), naturalArmorClass, 0, "1d6", 0, List.of(), 0);
        GameMonster monster = new GameMonster(UUID.randomUUID(), template.getName(), template.getId(), room.getId(),
                template.getAttributes(), maxHealth);
        monster.attachTemplate(template);
        monster.setCurrentRoom(room);
        room.addMonster(monster);
        return monster;
    }

    private Item weapon(String damageDice) {
        return weapon(damageDice, 0, WeaponCategory.MARTIAL);
    }

    private Item weapon(String damageDice, int bonus) {
        return weapon(damageDice, bonus, WeaponCategory.MARTIAL);
    }

    private Item weapon(String damageDice, int bonus, WeaponCategory category) {
        ItemTemplate template = new ItemTemplate(UUID.randomUUID(), "Épée", null, ItemType.WEAPON, 3, null, 0,
                damageDice, category, 0, Rarity.COMMON, bonus);
        Item item = new Item(UUID.randomUUID(), template.getId(), null, null, EquipmentSlot.WEAPON);
        item.attachTemplate(template);
        return item;
    }

    private double averageCheckTotal(GamePlayer character, Skill skill, int iterations) {
        long total = 0;
        for (int i = 0; i < iterations; i++) {
            total += character.check(skill, 0).total();
        }
        return (double) total / iterations;
    }

    private double averageSaveTotal(GamePlayer character, Attribute attribute, int iterations) {
        long total = 0;
        for (int i = 0; i < iterations; i++) {
            total += character.save(attribute, 0).total();
        }
        return (double) total / iterations;
    }

    // N'appelle pas GamePlayer#equipItem : celui-ci publie un événement de domaine
    // via DomainEventPublisher, qui suppose un contexte Spring initialisé
    // (voir sa Javadoc) — absent de ce test unitaire pur. On construit donc
    // directement l'Item avec son slot déjà renseigné.
    private void equip(GamePlayer character, Item item) {
        character.getInventory().addItem(item);
    }

    private Item armor(String name, ArmorCategory category, int baseAc) {
        return armor(name, category, baseAc, 0);
    }

    private Item armor(String name, ArmorCategory category, int baseAc, int bonus) {
        ItemTemplate template = new ItemTemplate(UUID.randomUUID(), name, null, ItemType.ARMOR, 5, category, baseAc,
                null, null, 0, Rarity.COMMON, bonus);
        Item item = new Item(UUID.randomUUID(), template.getId(), null, null, EquipmentSlot.CHEST);
        item.attachTemplate(template);
        return item;
    }

    private Item shield(int bonus) {
        ItemTemplate template = new ItemTemplate(UUID.randomUUID(), "Bouclier", null, ItemType.SHIELD, 3, null, 2, null,
                null, 0, Rarity.COMMON, bonus);
        Item item = new Item(UUID.randomUUID(), template.getId(), null, null, EquipmentSlot.OFF_HAND);
        item.attachTemplate(template);
        return item;
    }

    private GamePlayer character(int strength, int dexterity, int constitution, int intelligence, int wisdom,
            int charisma, int level) {
        return character(strength, dexterity, constitution, intelligence, wisdom, charisma, level,
                CharacterClass.FIGHTER);
    }

    private GamePlayer character(int strength, int dexterity, int constitution, int intelligence, int wisdom,
            int charisma, int level, CharacterClass characterClass) {
        return new GamePlayer(UUID.randomUUID(), UUID.randomUUID(), "Test", UUID.randomUUID(), Gender.MAN, Race.HUMAN,
                characterClass, level, 10, 10,
                TestAttributes.of(strength, dexterity, constitution, intelligence, wisdom, charisma), 0, 0);
    }
}
