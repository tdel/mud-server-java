package fr.idev.mudserver.network.message.ingame;

import fr.idev.mudserver.domain.actor.Attribute;
import fr.idev.mudserver.domain.actor.instance.MonsterInstance;
import fr.idev.mudserver.domain.actor.system.AttributeSystem;
import fr.idev.mudserver.telnet.Ansi;
import fr.idev.mudserver.telnet.OutputTelnetMessage;
import fr.idev.mudserver.telnet.TelnetOutput;

public record MonsterStatBlock(MonsterInstance monster) implements OutputTelnetMessage {

    @Override
    public void toTelnet(TelnetOutput output) {
        MonsterInstance m = monster;
        output.write(String.format(
                "== %s ==\n%s\nHealth: %d/%d\nArmor Class: %d\nStrength: %d (%+d)  Dexterity: %d (%+d)  Constitution: %d (%+d)\nIntelligence: %d (%+d)  Wisdom: %d (%+d)  Charisma: %d (%+d)\n",
                Ansi.monster(m.getName()), m.getDescription(), m.getCurrentHealth(), m.getMaxHealth(),
                m.getArmorClass(), AttributeSystem.getAttribute(m, Attribute.STRENGTH),
                AttributeSystem.getModifier(m, Attribute.STRENGTH),
                AttributeSystem.getAttribute(m, Attribute.DEXTERITY),
                AttributeSystem.getModifier(m, Attribute.DEXTERITY),
                AttributeSystem.getAttribute(m, Attribute.CONSTITUTION),
                AttributeSystem.getModifier(m, Attribute.CONSTITUTION),
                AttributeSystem.getAttribute(m, Attribute.INTELLIGENCE),
                AttributeSystem.getModifier(m, Attribute.INTELLIGENCE),
                AttributeSystem.getAttribute(m, Attribute.WISDOM), AttributeSystem.getModifier(m, Attribute.WISDOM),
                AttributeSystem.getAttribute(m, Attribute.CHARISMA),
                AttributeSystem.getModifier(m, Attribute.CHARISMA)));
    }
}
