package fr.idev.mudserver.domain;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Un NPC n'est aujourd'hui qu'un nom et une localisation (voir
 * {@code NpcService.warmNpcs}) — pas de template comme {@link GameMonster},
 * rien à dédupliquer entre instances. Il hérite malgré tout
 * {@code attributes}/{@code currentHealth}/{@code maxHealth} de
 * {@link GameCharacter} : ces champs restent neutres et non exploités par
 * aucune règle pour l'instant (valeurs nominales ci-dessous), prêts si un NPC
 * devient un jour attaquable.
 */
public final class GameNpc extends GameCharacter {

    private static final int NOMINAL_HEALTH = 1;

    private final UUID roomId;

    public GameNpc(UUID id, String name, UUID roomId) {
        super(id, name, neutralAttributes(), NOMINAL_HEALTH, NOMINAL_HEALTH);
        this.roomId = roomId;
    }

    public UUID getRoomId() {
        return roomId;
    }

    private static Map<Attribute, Integer> neutralAttributes() {
        Map<Attribute, Integer> attributes = new EnumMap<>(Attribute.class);
        for (Attribute attribute : Attribute.values()) {
            attributes.put(attribute, 10);
        }
        return attributes;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof GameNpc other)) {
            return false;
        }
        return Objects.equals(getId(), other.getId()) && Objects.equals(getName(), other.getName())
                && Objects.equals(roomId, other.roomId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId(), getName(), roomId);
    }

    @Override
    public String toString() {
        return "GameNpc[id=" + getId() + ", name=" + getName() + ", roomId=" + roomId + "]";
    }
}
