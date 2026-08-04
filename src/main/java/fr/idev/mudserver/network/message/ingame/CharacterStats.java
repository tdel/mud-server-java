package fr.idev.mudserver.network.message.ingame;

import fr.idev.mudserver.domain.Attribute;
import fr.idev.mudserver.domain.Character;
import fr.idev.mudserver.telnet.OutputTelnetMessage;
import fr.idev.mudserver.telnet.TelnetOutput;

public record CharacterStats(Character character) implements OutputTelnetMessage {

    @Override
    public void toTelnet(TelnetOutput output) {
        Character c = character;
        output.write(String.format(
                "== %s (Level %d) ==\nHealth: %d/%d\nProficiency: %+d\nStrength: %d (%+d)  Dexterity: %d (%+d)  Constitution: %d (%+d)\nIntelligence: %d (%+d)  Wisdom: %d (%+d)  Charisma: %d (%+d)\n",
                c.getName(), c.getLevel(), c.getCurrentHealth(), c.getMaxHealth(), c.getProficiencyBonus(),
                c.getAttribute(Attribute.STRENGTH), c.getModifier(Attribute.STRENGTH),
                c.getAttribute(Attribute.DEXTERITY), c.getModifier(Attribute.DEXTERITY),
                c.getAttribute(Attribute.CONSTITUTION), c.getModifier(Attribute.CONSTITUTION),
                c.getAttribute(Attribute.INTELLIGENCE), c.getModifier(Attribute.INTELLIGENCE),
                c.getAttribute(Attribute.WISDOM), c.getModifier(Attribute.WISDOM), c.getAttribute(Attribute.CHARISMA),
                c.getModifier(Attribute.CHARISMA)));
    }
}
