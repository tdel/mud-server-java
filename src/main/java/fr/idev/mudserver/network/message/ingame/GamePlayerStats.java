package fr.idev.mudserver.network.message.ingame;

import fr.idev.mudserver.domain.actor.component.IdentityComponent;

import java.util.stream.Collectors;

import fr.idev.mudserver.domain.actor.component.AppearanceComponent;
import fr.idev.mudserver.domain.actor.component.CombatComponent;
import fr.idev.mudserver.domain.actor.component.LevelingComponent;
import fr.idev.mudserver.domain.actor.instance.CharacterInstance;
import fr.idev.mudserver.domain.actor.Skill;
import fr.idev.mudserver.telnet.Ansi;
import fr.idev.mudserver.telnet.OutputTelnetMessage;
import fr.idev.mudserver.telnet.TelnetOutput;

public record GamePlayerStats(CharacterInstance character, int armorClass, int proficiencyBonus, int strength,
        int strengthModifier, int dexterity, int dexterityModifier, int constitution, int constitutionModifier,
        int intelligence, int intelligenceModifier, int wisdom, int wisdomModifier, int charisma,
        int charismaModifier) implements OutputTelnetMessage {

    @Override
    public void toTelnet(TelnetOutput output) {
        CharacterInstance c = character;
        CombatComponent combat = c.component(CombatComponent.class);
        output.write(String.format(
                "== %s (%s, Level %d %s) ==\nHealth: %d/%d\nArmor Class: %d\nProficiency: %+d\nStrength: %d (%+d)  Dexterity: %d (%+d)  Constitution: %d (%+d)\nIntelligence: %d (%+d)  Wisdom: %d (%+d)  Charisma: %d (%+d)\nPrimary Ability: %s\nSaving Throws: %s\nSkills: %s\n",
                Ansi.player(c.component(IdentityComponent.class).name()),
                c.component(AppearanceComponent.class).gender().label(), c.component(LevelingComponent.class).level(),
                c.component(AppearanceComponent.class).characterClass().label(), combat.currentHealth(),
                combat.maxHealth(), armorClass, proficiencyBonus, strength, strengthModifier, dexterity,
                dexterityModifier, constitution, constitutionModifier, intelligence, intelligenceModifier, wisdom,
                wisdomModifier, charisma, charismaModifier,
                c.component(AppearanceComponent.class).characterClass().primaryAbility().label(),
                c.component(AppearanceComponent.class).characterClass().savingThrowProficiencies().stream().sorted()
                        .map(fr.idev.mudserver.domain.actor.Attribute::label).collect(Collectors.joining(", ")),
                c.component(AppearanceComponent.class).characterClass().skillProficiencies().stream().sorted()
                        .map(Skill::label).collect(Collectors.joining(", "))));
    }
}
