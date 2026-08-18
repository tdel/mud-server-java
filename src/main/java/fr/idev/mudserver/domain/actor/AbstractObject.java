package fr.idev.mudserver.domain.actor;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import fr.idev.mudserver.game.ECS;

public abstract class AbstractObject {

    private final UUID id;
    private final ECS ecs;

    // Composant requis à attacher par l'appelant : IdentityComponent
    protected AbstractObject(UUID id, ECS ecs) {
        this.id = id;
        this.ecs = ecs;
    }

    public UUID getId() {
        return id;
    }

    public <C> void attachComponent(C component) {
        ecs.attach(this, component);
    }

    public <C> void detachComponent(Class<C> type) {
        ecs.detach(this, type);
    }

    public <C> Optional<C> findComponent(Class<C> type) {
        return ecs.find(this, type);
    }

    public <C> C component(Class<C> type) {
        return findComponent(type).orElseThrow(
                () -> new IllegalStateException("No " + type.getSimpleName() + " attached to entity " + id));
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
        return "AbstractObject[id=" + getId() + "]";
    }
}
