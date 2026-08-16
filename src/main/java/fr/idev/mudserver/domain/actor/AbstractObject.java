package fr.idev.mudserver.domain.actor;

import fr.idev.mudserver.domain.actor.instance.MonsterInstance;
import fr.idev.mudserver.domain.actor.system.AttributeSystem;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;

public abstract class AbstractObject {

    private UUID id;
    private String name;
    private final Map<Class<?>, Object> components = new ConcurrentHashMap<>();

    protected AbstractObject(UUID id, String name) {
        this.id = id;
        this.name = name;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public <C> void attachComponent(C component) {
        components.put(component.getClass(), component);
    }

    public <C> Optional<C> findComponent(Class<C> type) {
        return Optional.ofNullable(type.cast(components.get(type)));
    }

    public <C> C component(Class<C> type) {
        return findComponent(type).orElseThrow(
                () -> new IllegalStateException("No " + type.getSimpleName() + " attached to entity " + id));
    }

    @SuppressWarnings("unchecked")
    public <C> C updateComponent(Class<C> type, UnaryOperator<C> mutator) {
        return (C) components.compute(type, (t, current) -> mutator.apply((C) current));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AbstractObject other)) {
            return false;
        }

        return this.id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId());
    }

    @Override
    public String toString() {
        return "AbstractObject[id=" + getId() + ", name=" + getName() + "]";
    }
}
