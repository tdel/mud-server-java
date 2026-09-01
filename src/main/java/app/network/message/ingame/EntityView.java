package app.network.message.ingame;

import java.util.List;
import java.util.UUID;

import app.domain.actor.AbstractCharacter;
import app.domain.map.Position;
import app.game.engine.MovementEngine;

public record EntityView(UUID id, String name, double x, double y, double heading, double speed, int currentHealth,
        int maxHealth, int level, Double targetX, Double targetY) {

    public static EntityView of(AbstractCharacter character) {
        MovementEngine.ActiveMovement movement = character.getMotionSystem().getActiveMovement();
        Double targetX = null;
        Double targetY = null;
        if (movement != null && !movement.remainingWaypoints().isEmpty()) {
            List<Position> waypoints = movement.remainingWaypoints();
            Position destination = waypoints.get(waypoints.size() - 1);
            targetX = destination.x();
            targetY = destination.y();
        }
        return new EntityView(character.getId(), character.getName(), character.getMotionSystem().getPosition().x(),
                character.getMotionSystem().getPosition().y(), character.getMotionSystem().getHeading(),
                MovementEngine.unitsPerSecond(character.getMotionSystem().getSpeed()), character.getCurrentHealth(),
                character.getMaxHealth(), character.getLevel(), targetX, targetY);
    }
}
