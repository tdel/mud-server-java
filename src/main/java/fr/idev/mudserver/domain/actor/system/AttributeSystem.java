package fr.idev.mudserver.domain.actor.system;

import java.util.Map;

import fr.idev.mudserver.domain.actor.AbstractCharacter;
import fr.idev.mudserver.domain.actor.Attribute;
import fr.idev.mudserver.domain.actor.component.AttributeComponent;

public final class AttributeSystem {

    private AttributeSystem() {
    }

    public static int getAttribute(AbstractCharacter character, Attribute attribute) {
        return character.component(AttributeComponent.class).attributes().get(attribute);
    }

    public static int getModifier(AbstractCharacter character, Attribute attribute) {
        return Math.floorDiv(getAttribute(character, attribute) - 10, 2);
    }

    public static Map<Attribute, Integer> getAttributes(AbstractCharacter character) {
        return Map.copyOf(character.component(AttributeComponent.class).attributes());
    }
}
