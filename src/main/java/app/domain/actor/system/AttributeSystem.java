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

    // Bonus L2 de l'attribut en % (STR 40 -> +20, WIT 11 -> -36), cf.
    // Attribute.bonus.
    public int getModifier(Attribute attribute) {
        return (int) Math.round((attribute.bonus(getAttribute(attribute)) - 1.0) * 100);
    }

    public Map<Attribute, Integer> getAttributes() {
        return Map.copyOf(attributes);
    }
}
