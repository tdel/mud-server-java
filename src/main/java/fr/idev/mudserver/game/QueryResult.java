package fr.idev.mudserver.game;

import java.util.Map;

import fr.idev.mudserver.domain.actor.AbstractObject;

public record QueryResult(AbstractObject entity, Map<Class<?>, Object> components) {

    public <C> C component(Class<C> type) {
        return type.cast(components.get(type));
    }
}
