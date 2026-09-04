package app.domain.actor;

import app.domain.world.AbstractZone;
import app.domain.world.NormalZone;

import java.util.UUID;

public abstract class AbstractObject {

    private final UUID id;
    private String name;
    // Nom du clan pour un joueur (pas encore implémenté), fonction pour un PNJ
    // (ex. "Blacksmith", "City Guard") ; null si aucun titre n'est défini.
    private String title;
    private AbstractZone zone = NormalZone.INSTANCE;

    protected AbstractObject(UUID id, String name) {
        this.id = id;
        this.name = name;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public AbstractZone getZone() {
        return zone;
    }

    public void setZone(AbstractZone zone) {
        this.zone = zone;
    }
}
