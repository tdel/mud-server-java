package fr.idev.mudserver.network.message.ingame;

import java.util.stream.Collectors;

import fr.idev.mudserver.domain.actor.Attribute;
import fr.idev.mudserver.domain.actor.component.AppearanceComponent;
import fr.idev.mudserver.domain.actor.component.CombatComponent;
import fr.idev.mudserver.domain.actor.component.LevelingComponent;
import fr.idev.mudserver.domain.actor.instance.CharacterInstance;
import fr.idev.mudserver.domain.actor.system.AttributeSystem;
import fr.idev.mudserver.domain.actor.system.LevelingSystem;
import fr.idev.mudserver.domain.actor.Skill;
import fr.idev.mudserver.telnet.Ansi;
import fr.idev.mudserver.telnet.OutputTelnetMessage;
import fr.idev.mudserver.telnet.TelnetOutput;

public record GamePlayerStats(CharacterInstance character) implements OutputTelnetMessage {

    @Override
    public void toTelnet(TelnetOutput output) {
        CharacterInstance c = character;
        CombatComponent combat = c.component(CombatComponent.class);
        output.write(String.format(
                "== %s (%s, Level %d %s) ==\nHealth: %d/%d\nArmor Class: %d\nProficiency: %+d\nStrength: %d (%+d)  Dexterity: %d (%+d)  Constitution: %d (%+d)\nIntelligence: %d (%+d)  Wisdom: %d (%+d)  Charisma: %d (%+d)\nPrimary Ability: %s\nSaving Throws: %s\nSkills: %s\n",
                Ansi.player(c.getName()), c.component(AppearanceComponent.class).gender().label(),
                c.component(LevelingComponent.class).level(),
                c.component(AppearanceComponent.class).characterClass().label(), combat.currentHealth(),
                combat.maxHealth(), c.getArmorClass(), LevelingSystem.getProficiencyBonus(c),
                AttributeSystem.getAttribute(c, Attribute.STRENGTH), AttributeSystem.getModifier(c, Attribute.STRENGTH),
                AttributeSystem.getAttribute(c, Attribute.DEXTERITY),
                AttributeSystem.getModifier(c, Attribute.DEXTERITY),
                AttributeSystem.getAttribute(c, Attribute.CONSTITUTION),
                AttributeSystem.getModifier(c, Attribute.CONSTITUTION),
                AttributeSystem.getAttribute(c, Attribute.INTELLIGENCE),
                AttributeSystem.getModifier(c, Attribute.INTELLIGENCE),
                AttributeSystem.getAttribute(c, Attribute.WISDOM), AttributeSystem.getModifier(c, Attribute.WISDOM),
                AttributeSystem.getAttribute(c, Attribute.CHARISMA), AttributeSystem.getModifier(c, Attribute.CHARISMA),
                c.component(AppearanceComponent.class).characterClass().primaryAbility().label(),
                c.component(AppearanceComponent.class).characterClass().savingThrowProficiencies().stream().sorted()
                        .map(Attribute::label).collect(Collectors.joining(", ")),
                c.component(AppearanceComponent.class).characterClass().skillProficiencies().stream().sorted()
                        .map(Skill::label).collect(Collectors.joining(", "))));
    }
}
