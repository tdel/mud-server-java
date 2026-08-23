package fr.idev.mudserver.network.message.ingame;

import fr.idev.mudserver.network.OutputJsonMessage;
import fr.idev.mudserver.domain.actor.Attribute;
import fr.idev.mudserver.domain.actor.instance.MonsterInstance;
import fr.idev.mudserver.network.server.tcpjson.TcpJsonOutput;

public record MonsterStatBlock(MonsterInstance monster) implements OutputJsonMessage {

    public record AttributeScore(int score, int modifier) {
    }

    public record Payload(String name, String description, int currentHealth, int maxHealth, int armorClass,
            AttributeScore strength, AttributeScore dexterity, AttributeScore constitution, AttributeScore intelligence,
            AttributeScore wisdom, AttributeScore charisma) {
    }

    @Override
    public void toJson(TcpJsonOutput output) {
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
}
