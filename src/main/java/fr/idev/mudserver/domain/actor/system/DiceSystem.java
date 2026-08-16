package fr.idev.mudserver.domain.actor.system;

import fr.idev.mudserver.domain.actor.AbstractCharacter;
import fr.idev.mudserver.domain.actor.Attribute;
import fr.idev.mudserver.domain.actor.Skill;
import fr.idev.mudserver.domain.actor.component.AppearanceComponent;
import fr.idev.mudserver.domain.actor.instance.CharacterInstance;
import fr.idev.mudserver.game.dice.CheckResult;
import fr.idev.mudserver.game.dice.DiceExpression;
import fr.idev.mudserver.game.dice.DiceRoll;
import fr.idev.mudserver.game.dice.DiceRoller;

public final class DiceSystem {

    private DiceSystem() {
    }

    public static int rollInitiative(AbstractCharacter character) {
        return DiceRoller.roll(new DiceExpression(1, 20, AttributeSystem.getModifier(character, Attribute.DEXTERITY)))
                .total();
    }

    public static CheckResult check(CharacterInstance character, Skill skill, int dc) {
        boolean proficient = character.component(AppearanceComponent.class).characterClass().skillProficiencies()
                .contains(skill);
        return checkOrSave(character, skill.getGoverningAttribute(), proficient, dc, skill.label());
    }

    public static CheckResult save(CharacterInstance character, Attribute attribute, int dc) {
        boolean proficient = character.component(AppearanceComponent.class).characterClass().savingThrowProficiencies()
                .contains(attribute);
        return checkOrSave(character, attribute, proficient, dc, attribute.label());
    }

    private static CheckResult checkOrSave(CharacterInstance character, Attribute attribute, boolean proficient, int dc,
            String label) {
        int modifier = AttributeSystem.getModifier(character, attribute)
                + (proficient ? LevelingSystem.getProficiencyBonus(character) : 0);
        boolean disadvantage = (attribute == Attribute.STRENGTH || attribute == Attribute.DEXTERITY)
                && InventorySystem.isWearingNonProficientArmor(character);
        DiceRoll diceRoll = DiceRoller.rollD20(modifier, disadvantage);
        boolean success = diceRoll.total() >= dc;
        return new CheckResult(label, diceRoll.total(), dc, proficient, disadvantage, success);
    }

}
