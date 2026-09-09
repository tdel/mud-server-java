package app.domain.actor.system;

import java.util.EnumMap;
import java.util.Map;

import app.domain.actor.Attribute;

public final class AttributeSystem {

    private final Map<Attribute, Integer> attributes;

    public AttributeSystem(Map<Attribute, Integer> attributes) {
        this.attributes = new EnumMap<>(attributes);
    }

    public int getAttribute(Attribute attribute) {
        return attributes.get(attribute);
    }

    public int getModifier(Attribute attribute) {
        return Math.floorDiv(getAttribute(attribute) - 10, 2);
    }

    public Map<Attribute, Integer> getAttributes() {
        return Map.copyOf(attributes);
    }
}
