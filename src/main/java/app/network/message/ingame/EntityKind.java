package app.network.message.ingame;

import app.domain.actor.AbstractCharacter;
import app.domain.actor.AbstractNpc;
import app.domain.actor.instance.CharacterInstance;
import app.domain.actor.instance.MonsterInstance;

public enum EntityKind {
    PLAYER, MONSTER, NPC;

    public static EntityKind of(AbstractCharacter character) {
        return switch (character) {
            case CharacterInstance c -> PLAYER;
            case MonsterInstance m -> MONSTER;
            case AbstractNpc n -> NPC;
            default -> throw new IllegalArgumentException("Type de personnage inconnu : " + character.getClass());
        };
    }
}
