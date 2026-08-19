package fr.idev.mudserver.network.message.ingame;

import java.util.List;
import java.util.stream.Collectors;

import fr.idev.mudserver.network.OutputJsonMessage;
import fr.idev.mudserver.domain.actor.Attribute;
import fr.idev.mudserver.domain.actor.instance.CharacterInstance;
import fr.idev.mudserver.domain.actor.Skill;
import fr.idev.mudserver.network.server.telnet.Ansi;
import fr.idev.mudserver.network.server.telnet.OutputTelnetMessage;
import fr.idev.mudserver.network.server.telnet.TelnetOutput;
import fr.idev.mudserver.network.server.tui.JsonOutput;

public record GamePlayerStats(CharacterInstance character) implements OutputTelnetMessage, OutputJsonMessage {

    public record AttributeScore(int score, int modifier) {
    }

    public record Payload(String name, String gender, int level, String characterClass, int currentHealth,
            int maxHealth, int armorClass, int proficiencyBonus, AttributeScore strength, AttributeScore dexterity,
            AttributeScore constitution, AttributeScore intelligence, AttributeScore wisdom, AttributeScore charisma,
            String primaryAbility, List<String> savingThrowProficiencies, List<String> skillProficiencies) {
    }

    @Override
    public void toJson(JsonOutput output) {
        CharacterInstance c = character;
        output.write("GamePlayerStats",
                new Payload(c.getName(), c.getGender().label(), c.getLevel(), c.getCharacterClass().label(),
                        c.getCurrentHealth(), c.getMaxHealth(), c.getArmorClass(), c.getProficiencyBonus(),
                        attributeScore(c, Attribute.STRENGTH), attributeScore(c, Attribute.DEXTERITY),
                        attributeScore(c, Attribute.CONSTITUTION), attributeScore(c, Attribute.INTELLIGENCE),
                        attributeScore(c, Attribute.WISDOM), attributeScore(c, Attribute.CHARISMA),
                        c.getPrimaryAbility().label(),
                        c.getSavingThrowProficiencies().stream().sorted().map(Attribute::label).toList(),
                        c.getSkillProficiencies().stream().sorted().map(Skill::label).toList()),
                false);
    }

    private static AttributeScore attributeScore(CharacterInstance c, Attribute attribute) {
        return new AttributeScore(c.getAttribute(attribute), c.getModifier(attribute));
    }

    @Override
    public void toTelnet(TelnetOutput output) {
        CharacterInstance c = character;
        output.write(String.format(
                "== %s (%s, Level %d %s) ==\nHealth: %d/%d\nArmor Class: %d\nProficiency: %+d\nStrength: %d (%+d)  Dexterity: %d (%+d)  Constitution: %d (%+d)\nIntelligence: %d (%+d)  Wisdom: %d (%+d)  Charisma: %d (%+d)\nPrimary Ability: %s\nSaving Throws: %s\nSkills: %s\n",
                Ansi.player(c.getName()), c.getGender().label(), c.getLevel(), c.getCharacterClass().label(),
                c.getCurrentHealth(), c.getMaxHealth(), c.getArmorClass(), c.getProficiencyBonus(),
                c.getAttribute(Attribute.STRENGTH), c.getModifier(Attribute.STRENGTH),
                c.getAttribute(Attribute.DEXTERITY), c.getModifier(Attribute.DEXTERITY),
                c.getAttribute(Attribute.CONSTITUTION), c.getModifier(Attribute.CONSTITUTION),
                c.getAttribute(Attribute.INTELLIGENCE), c.getModifier(Attribute.INTELLIGENCE),
                c.getAttribute(Attribute.WISDOM), c.getModifier(Attribute.WISDOM), c.getAttribute(Attribute.CHARISMA),
                c.getModifier(Attribute.CHARISMA), c.getPrimaryAbility().label(),
                c.getSavingThrowProficiencies().stream().sorted().map(Attribute::label)
                        .collect(Collectors.joining(", ")),
                c.getSkillProficiencies().stream().sorted().map(Skill::label).collect(Collectors.joining(", "))));
    }
}
