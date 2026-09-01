package app.domain.actor.system;

import app.domain.actor.AbstractCharacter;
import app.domain.actor.ModifiedStat;
import app.domain.actor.event.CharacterPositionChanged;
import app.domain.actor.event.DomainEventPublisher;
import app.domain.actor.instance.CharacterInstance;
import app.domain.map.Position;
import app.domain.world.AbstractZone;
import app.domain.world.MapInstance;
import app.domain.world.NormalZone;
import app.game.engine.MovementEngine;

public final class MotionSystem {

    private final AbstractCharacter character;
    private volatile MapInstance currentMap;
    private volatile Position position;
    private volatile double heading;
    private volatile MovementEngine.ActiveMovement activeMovement;

    public MotionSystem(AbstractCharacter character) {
        this.character = character;
    }

    public MapInstance getCurrentMap() {
        return currentMap;
    }

    public void setCurrentMap(MapInstance currentMap) {
        this.currentMap = currentMap;
    }

    public Position getPosition() {
        return position;
    }

    public void setPosition(Position position) {
        this.position = position;
        AbstractZone newZone = currentMap != null && position != null
                ? currentMap.zoneAt(position)
                : NormalZone.INSTANCE;
        if (newZone != character.getZone()) {
            character.getZone().onObjectExiting(character);
            character.setZone(newZone);
            newZone.onObjectEntering(character);
        }
        if (position != null && character instanceof CharacterInstance ci) {
            DomainEventPublisher.publish(new CharacterPositionChanged(ci));
        }
    }

    public double getHeading() {
        return heading;
    }

    public void setHeading(double heading) {
        this.heading = heading;
    }

    public int getSpeed() {
        return character.getStatSystem().getEffective(ModifiedStat.SPEED);
    }

    public MovementEngine.ActiveMovement getActiveMovement() {
        return activeMovement;
    }

    public void updateMovement(MovementEngine.ActiveMovement movement) {
        this.activeMovement = movement;
    }

    public void clearMovement() {
        this.activeMovement = null;
    }
}
