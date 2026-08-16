package fr.idev.mudserver.domain.actor.system;

import org.springframework.stereotype.Service;

import fr.idev.mudserver.domain.actor.AbstractCharacter;
import fr.idev.mudserver.domain.actor.Attribute;
import fr.idev.mudserver.domain.actor.Skill;
import fr.idev.mudserver.domain.actor.component.AppearanceComponent;
import fr.idev.mudserver.domain.actor.instance.CharacterInstance;
import fr.idev.mudserver.game.dice.CheckResult;
import fr.idev.mudserver.game.dice.DiceExpression;
import fr.idev.mudserver.game.dice.DiceRoll;
import fr.idev.mudserver.game.dice.DiceRoller;

@Service
public class DiceSystem {

    private final InventorySystem inventorySystem;
    private final AttributeSystem attributeSystem;
    private final LevelingSystem levelingSystem;

    public DiceSystem(InventorySystem inventorySystem, AttributeSystem attributeSystem, LevelingSystem levelingSystem) {
        this.inventorySystem = inventorySystem;
        this.attributeSystem = attributeSystem;
        this.levelingSystem = levelingSystem;
    }

    public int rollInitiative(AbstractCharacter character) {
        return DiceRoller.roll(new DiceExpression(1, 20, attributeSystem.getModifier(character, Attribute.DEXTERITY)))
                .total();
    }

    public CheckResult check(CharacterInstance character, Skill skill, int dc) {
        boolean proficient = character.component(AppearanceComponent.class).characterClass().skillProficiencies()
                .contains(skill);
        return checkOrSave(character, skill.getGoverningAttribute(), proficient, dc, skill.label());
    }

    public CheckResult save(CharacterInstance character, Attribute attribute, int dc) {
        boolean proficient = character.component(AppearanceComponent.class).characterClass().savingThrowProficiencies()
                .contains(attribute);
        return checkOrSave(character, attribute, proficient, dc, attribute.label());
    }

    private CheckResult checkOrSave(CharacterInstance character, Attribute attribute, boolean proficient, int dc,
            String label) {
        int modifier = attributeSystem.getModifier(character, attribute)
                + (proficient ? levelingSystem.getProficiencyBonus(character) : 0);
        boolean disadvantage = (attribute == Attribute.STRENGTH || attribute == Attribute.DEXTERITY)
                && inventorySystem.isWearingNonProficientArmor(character);
        DiceRoll diceRoll = DiceRoller.rollD20(modifier, disadvantage);
        boolean success = diceRoll.total() >= dc;
        return new CheckResult(label, diceRoll.total(), dc, proficient, disadvantage, success);
    }

}
