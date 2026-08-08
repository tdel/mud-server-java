package fr.idev.mudserver.network.message.ingame;

import fr.idev.mudserver.domain.actor.Attribute;
import fr.idev.mudserver.domain.actor.GameMonster;
import fr.idev.mudserver.telnet.Ansi;
import fr.idev.mudserver.telnet.OutputTelnetMessage;
import fr.idev.mudserver.telnet.TelnetOutput;

public record MonsterStatBlock(GameMonster monster) implements OutputTelnetMessage {

    @Override
    public void toTelnet(TelnetOutput output) {
        GameMonster m = monster;
        output.write(String.format(
                "== %s ==\n%s\nHealth: %d/%d\nArmor Class: %d\nStrength: %d (%+d)  Dexterity: %d (%+d)  Constitution: %d (%+d)\nIntelligence: %d (%+d)  Wisdom: %d (%+d)  Charisma: %d (%+d)\n",
                Ansi.monster(m.getName()), m.getDescription(), m.getCurrentHealth(), m.getMaxHealth(),
                m.getArmorClass(), m.getAttribute(Attribute.STRENGTH), m.getModifier(Attribute.STRENGTH),
                m.getAttribute(Attribute.DEXTERITY), m.getModifier(Attribute.DEXTERITY),
                m.getAttribute(Attribute.CONSTITUTION), m.getModifier(Attribute.CONSTITUTION),
                m.getAttribute(Attribute.INTELLIGENCE), m.getModifier(Attribute.INTELLIGENCE),
                m.getAttribute(Attribute.WISDOM), m.getModifier(Attribute.WISDOM), m.getAttribute(Attribute.CHARISMA),
                m.getModifier(Attribute.CHARISMA)));
    }
}
