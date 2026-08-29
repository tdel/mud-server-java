package app.domain.world;

import app.domain.actor.AbstractCharacter;
import app.domain.map.Position;

public abstract class AbstractZone {

    public abstract String getName();

    // Défaut pour NormalZone : jamais matchée par géométrie, seulement en repli
    // (voir MapTemplate.zoneAt) quand aucune zone plus spécifique ne contient la position.
    public boolean contains(Position position) {
        return false;
    }

    public void onObjectEntering(AbstractCharacter character) {
    }

    public void onObjectExiting(AbstractCharacter character) {
    }
}
