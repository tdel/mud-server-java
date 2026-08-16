package fr.idev.mudserver.network.message.ingame;

import java.util.stream.Collectors;

import fr.idev.mudserver.domain.actor.Attribute;
import fr.idev.mudserver.domain.actor.component.LevelingComponent;
import fr.idev.mudserver.domain.actor.instance.CharacterInstance;
import fr.idev.mudserver.domain.actor.Skill;
import fr.idev.mudserver.telnet.Ansi;
import fr.idev.mudserver.telnet.OutputTelnetMessage;
import fr.idev.mudserver.telnet.TelnetOutput;

public record GamePlayerStats(CharacterInstance character) implements OutputTelnetMessage {

    @Override
    public void toTelnet(TelnetOutput output) {
        CharacterInstance c = character;
        output.write(String.format(
                "== %s (%s, Level %d %s) ==\nHealth: %d/%d\nArmor Class: %d\nProficiency: %+d\nStrength: %d (%+d)  Dexterity: %d (%+d)  Constitution: %d (%+d)\nIntelligence: %d (%+d)  Wisdom: %d (%+d)  Charisma: %d (%+d)\nPrimary Ability: %s\nSaving Throws: %s\nSkills: %s\n",
                Ansi.player(c.getName()), c.getGender().label(), c.component(LevelingComponent.class).level(),
                c.getCharacterClass().label(), c.getCurrentHealth(), c.getMaxHealth(), c.getArmorClass(),
                c.getProficiencyBonus(), c.getAttribute(Attribute.STRENGTH), c.getModifier(Attribute.STRENGTH),
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
