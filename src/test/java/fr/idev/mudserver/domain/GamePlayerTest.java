package fr.idev.mudserver.domain;

import java.util.UUID;

import org.junit.jupiter.api.Test;

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
        equip(character, shield());

        assertThat(character.getArmorClass()).isEqualTo(15);
    }

    // N'appelle pas GamePlayer#equipItem : celui-ci publie un événement de domaine
    // via DomainEventPublisher, qui suppose un contexte Spring initialisé
    // (voir sa Javadoc) — absent de ce test unitaire pur. On construit donc
    // directement l'Item avec son slot déjà renseigné.
    private void equip(GamePlayer character, Item item) {
        character.addItem(item);
    }

    private Item armor(String name, ArmorCategory category, int baseAc) {
        ItemTemplate template = new ItemTemplate(UUID.randomUUID(), name, null, ItemType.ARMOR, 5, category, baseAc,
                null);
        Item item = new Item(UUID.randomUUID(), template.getId(), null, null, EquipmentSlot.CHEST);
        item.attachTemplate(template);
        return item;
    }

    private Item shield() {
        ItemTemplate template = new ItemTemplate(UUID.randomUUID(), "Bouclier", null, ItemType.SHIELD, 3, null, 2,
                null);
        Item item = new Item(UUID.randomUUID(), template.getId(), null, null, EquipmentSlot.OFF_HAND);
        item.attachTemplate(template);
        return item;
    }

    private GamePlayer character(int strength, int dexterity, int constitution, int intelligence, int wisdom,
            int charisma, int level) {
        return new GamePlayer(UUID.randomUUID(), UUID.randomUUID(), "Test", UUID.randomUUID(), Gender.MAN, Race.HUMAN,
                CharacterClass.FIGHTER, level, 10, 10,
                TestAttributes.of(strength, dexterity, constitution, intelligence, wisdom, charisma));
    }
}
