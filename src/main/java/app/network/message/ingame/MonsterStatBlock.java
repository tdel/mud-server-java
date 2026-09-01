package app.network.message.ingame;

import java.util.UUID;

import app.network.OutputJsonMessage;
import app.domain.actor.Attribute;
import app.domain.actor.ModifiedStat;
import app.domain.actor.instance.MonsterInstance;
import app.network.server.tcpjson.TcpJsonOutput;

public record MonsterStatBlock(MonsterInstance monster) implements OutputJsonMessage {

    public record AttributeScore(int score, int modifier) {
    }

    public record Payload(UUID id, String name, String description, int currentHealth, int maxHealth, int pAtk,
            int pDef, int mAtk, int mDef, int accuracy, int evasion, int criticalRate, int atkSpd,
            AttributeScore strength, AttributeScore dexterity, AttributeScore constitution, AttributeScore intelligence,
            AttributeScore wit, AttributeScore men) {
    }

    @Override
    public void toJson(TcpJsonOutput output) {
        MonsterInstance m = monster;
        output.write("MonsterStatBlock", new Payload(m.getId(), m.getName(), m.getDescription(), m.getCurrentHealth(),
                m.getMaxHealth(), m.getStatSystem().getEffective(ModifiedStat.PATK),
                m.getStatSystem().getEffective(ModifiedStat.PDEF), m.getStatSystem().getEffective(ModifiedStat.MATK),
                m.getStatSystem().getEffective(ModifiedStat.MDEF),
                m.getStatSystem().getEffective(ModifiedStat.ACCURACY),
                m.getStatSystem().getEffective(ModifiedStat.EVASION),
                m.getStatSystem().getEffective(ModifiedStat.PCRIT), m.getStatSystem().getEffective(ModifiedStat.ATKSPD),
                attributeScore(m, Attribute.STRENGTH), attributeScore(m, Attribute.DEXTERITY),
                attributeScore(m, Attribute.CONSTITUTION), attributeScore(m, Attribute.INTELLIGENCE),
                attributeScore(m, Attribute.WIT), attributeScore(m, Attribute.MEN)), false);
    }

    private static AttributeScore attributeScore(MonsterInstance m, Attribute attribute) {
        return new AttributeScore(m.getAttribute(attribute), m.getModifier(attribute));
    }
}
