package app.network.message.ingame;

import java.util.List;

import app.network.OutputJsonMessage;
import app.domain.actor.Attribute;
import app.domain.actor.instance.CharacterInstance;
import app.domain.actor.Skill;
import app.network.server.tcpjson.TcpJsonOutput;

public record GamePlayerStats(CharacterInstance character) implements OutputJsonMessage {

    public record AttributeScore(int score, int modifier) {
    }

    public record Payload(String name, String gender, int level, String characterClass, int currentHealth,
            int maxHealth, int currentMana, int maxMana, int armorClass, int proficiencyBonus, AttributeScore strength,
            AttributeScore dexterity, AttributeScore constitution, AttributeScore intelligence, AttributeScore wisdom,
            AttributeScore charisma, String primaryAbility, List<String> savingThrowProficiencies,
            List<String> skillProficiencies) {
    }

    @Override
    public void toJson(TcpJsonOutput output) {
        CharacterInstance c = character;
        output.write("GamePlayerStats",
                new Payload(c.getName(), c.getGender().label(), c.getLevel(), c.getCharacterClass().label(),
                        c.getCurrentHealth(), c.getMaxHealth(), c.getCurrentMana(), c.getMaxMana(), c.getArmorClass(),
                        c.getProficiencyBonus(), attributeScore(c, Attribute.STRENGTH),
                        attributeScore(c, Attribute.DEXTERITY), attributeScore(c, Attribute.CONSTITUTION),
                        attributeScore(c, Attribute.INTELLIGENCE), attributeScore(c, Attribute.WISDOM),
                        attributeScore(c, Attribute.CHARISMA), c.getPrimaryAbility().label(),
                        c.getSavingThrowProficiencies().stream().sorted().map(Attribute::label).toList(),
                        c.getSkillProficiencies().stream().sorted().map(Skill::label).toList()),
                false);
    }

    private static AttributeScore attributeScore(CharacterInstance c, Attribute attribute) {
        return new AttributeScore(c.getAttribute(attribute), c.getModifier(attribute));
    }
}
