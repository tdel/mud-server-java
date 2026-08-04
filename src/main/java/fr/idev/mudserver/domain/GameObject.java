package fr.idev.mudserver.domain;

import java.util.UUID;

/**
 * Racine de toute entité identifiable et nommée du monde du jeu (joueur,
 * monstre, PNJ — voir {@link GameCharacter}). Ne porte que l'identité, rien de
 * comportemental.
 */
public abstract class GameObject {

    private UUID id;
    private String name;

    protected GameObject(UUID id, String name) {
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
}
