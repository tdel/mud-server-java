package fr.idev.mudserver.domain.actor.component;

import java.util.Map;

import fr.idev.mudserver.domain.actor.Attribute;

public record AttributeComponent(Map<Attribute, Integer> attributes) {
}
