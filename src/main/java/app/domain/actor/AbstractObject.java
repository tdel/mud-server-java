package app.domain.actor;

import app.domain.world.AbstractZone;
import app.domain.world.NormalZone;

import java.util.UUID;

public abstract class AbstractObject {

    private UUID id;
    private String name;
    private AbstractZone zone = NormalZone.INSTANCE;

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

    public AbstractZone getZone() {
        return zone;
    }

    public void setZone(AbstractZone zone) {
        this.zone = zone;
    }
}
