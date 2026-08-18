package fr.idev.mudserver.game;

import java.util.ArrayList;
import java.util.List;

public final class Query {

    private final List<Class<?>> requirements = new ArrayList<>();

    public Query addRequirement(Class<?> componentType) {
        requirements.add(componentType);
        return this;
    }

    List<Class<?>> requirements() {
        return requirements;
    }
}
