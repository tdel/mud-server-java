package app.network.message.ingame;

import java.util.UUID;

import app.network.OutputJsonMessage;
import app.domain.actor.Attribute;
import app.domain.actor.instance.MonsterInstance;
import app.network.server.tcpjson.TcpJsonOutput;

public record MonsterStatBlock(MonsterInstance monster) implements OutputJsonMessage {

    public record AttributeScore(int score, int modifier) {
    }

    public record Payload(UUID id, String name, String description, int currentHealth, int maxHealth, int armorClass,
            AttributeScore strength, AttributeScore dexterity, AttributeScore constitution, AttributeScore intelligence,
            AttributeScore wisdom, AttributeScore charisma) {
    }

    @Override
    public void toJson(TcpJsonOutput output) {
        MonsterInstance m = monster;
        output.write("MonsterStatBlock",
                new Payload(m.getId(), m.getName(), m.getDescription(), m.getCurrentHealth(), m.getMaxHealth(),
                        m.getArmorClass(), attributeScore(m, Attribute.STRENGTH),
                        attributeScore(m, Attribute.DEXTERITY), attributeScore(m, Attribute.CONSTITUTION),
                        attributeScore(m, Attribute.INTELLIGENCE), attributeScore(m, Attribute.WISDOM),
                        attributeScore(m, Attribute.CHARISMA)),
                false);
    }

    private static AttributeScore attributeScore(MonsterInstance m, Attribute attribute) {
        return new AttributeScore(m.getAttribute(attribute), m.getModifier(attribute));
    }
}
