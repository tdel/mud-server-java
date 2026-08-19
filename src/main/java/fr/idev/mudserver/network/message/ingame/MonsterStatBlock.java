package fr.idev.mudserver.network.message.ingame;

import fr.idev.mudserver.network.OutputJsonMessage;
import fr.idev.mudserver.domain.actor.Attribute;
import fr.idev.mudserver.domain.actor.instance.MonsterInstance;
import fr.idev.mudserver.network.server.telnet.Ansi;
import fr.idev.mudserver.network.server.telnet.OutputTelnetMessage;
import fr.idev.mudserver.network.server.telnet.TelnetOutput;
import fr.idev.mudserver.network.server.tui.JsonOutput;

public record MonsterStatBlock(MonsterInstance monster) implements OutputTelnetMessage, OutputJsonMessage {

    public record AttributeScore(int score, int modifier) {
    }

    public record Payload(String name, String description, int currentHealth, int maxHealth, int armorClass,
            AttributeScore strength, AttributeScore dexterity, AttributeScore constitution, AttributeScore intelligence,
            AttributeScore wisdom, AttributeScore charisma) {
    }

    @Override
    public void toJson(JsonOutput output) {
        MonsterInstance m = monster;
        output.write("MonsterStatBlock",
                new Payload(m.getName(), m.getDescription(), m.getCurrentHealth(), m.getMaxHealth(), m.getArmorClass(),
                        attributeScore(m, Attribute.STRENGTH), attributeScore(m, Attribute.DEXTERITY),
                        attributeScore(m, Attribute.CONSTITUTION), attributeScore(m, Attribute.INTELLIGENCE),
                        attributeScore(m, Attribute.WISDOM), attributeScore(m, Attribute.CHARISMA)),
                false);
    }

    private static AttributeScore attributeScore(MonsterInstance m, Attribute attribute) {
        return new AttributeScore(m.getAttribute(attribute), m.getModifier(attribute));
    }

    @Override
    public void toTelnet(TelnetOutput output) {
        MonsterInstance m = monster;
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
