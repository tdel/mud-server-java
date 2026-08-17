package fr.idev.mudserver.game;

import java.util.ArrayList;
import java.util.List;

import fr.idev.mudserver.domain.actor.AbstractObject;

public final class Query {

    private final List<Class<?>> requirements = new ArrayList<>();

    public Query addRequirement(Class<?> componentType) {
        requirements.add(componentType);
        return this;
    }

    boolean matches(AbstractObject entity) {
        for (Class<?> requirement : requirements) {
            if (entity.findComponent(requirement).isEmpty()) {
                return false;
            }
        }
        return true;
    }
}
