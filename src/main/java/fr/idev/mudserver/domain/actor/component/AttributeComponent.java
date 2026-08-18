package fr.idev.mudserver.domain.actor.component;

import java.util.Map;

import fr.idev.mudserver.domain.actor.Attribute;

public class AttributeComponent {

    public Map<Attribute, Integer> attributes;

    public AttributeComponent(Map<Attribute, Integer> attributes) {
        this.attributes = attributes;
    }

    public int valueOf(Attribute attribute) {
        return attributes.get(attribute);
    }

    public int modifier(Attribute attribute) {
        return Math.floorDiv(valueOf(attribute) - 10, 2);
    }
}
