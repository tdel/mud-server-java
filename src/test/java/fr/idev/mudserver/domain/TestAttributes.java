package fr.idev.mudserver.domain;

import java.util.EnumMap;
import java.util.Map;

public final class TestAttributes {

    private TestAttributes() {
    }

    public static Map<Attribute, Integer> of(int strength, int dexterity, int constitution, int intelligence,
            int wisdom, int charisma) {
        Map<Attribute, Integer> attributes = new EnumMap<>(Attribute.class);
        attributes.put(Attribute.STRENGTH, strength);
        attributes.put(Attribute.DEXTERITY, dexterity);
        attributes.put(Attribute.CONSTITUTION, constitution);
        attributes.put(Attribute.INTELLIGENCE, intelligence);
        attributes.put(Attribute.WISDOM, wisdom);
        attributes.put(Attribute.CHARISMA, charisma);
        return attributes;
    }
}
