package app.network.message.ingame;

import java.util.List;
import java.util.UUID;

import app.network.OutputJsonMessage;
import app.domain.actor.AbstractCharacter;
import app.domain.map.Position;
import app.domain.world.ZoneInstance;
import app.game.engine.MovementEngine;
import app.network.server.tcpjson.TcpJsonOutput;

public record ViewAround(AbstractCharacter character) implements OutputJsonMessage {

    public record EntityView(UUID id, String name, double x, double y, double speed, int currentHealth, int maxHealth,
            int level, Double targetX, Double targetY) {
    }

    public record PortalView(double x, double y, String direction, String targetZoneName) {
    }

    public record WaypointView(double x, double y) {
    }

    public record Payload(String zoneName, String zoneDescription, double selfX, double selfY,
            List<EntityView> characters, List<EntityView> monsters, List<EntityView> npcs, List<PortalView> portals,
            List<WaypointView> remainingWaypoints) {
    }

    @Override
    public void toJson(TcpJsonOutput output) {
        ZoneInstance zone = character.getCurrentZone();
        Position self = character.getPosition();

        List<EntityView> characterViews = zone.characters().stream()
                .filter(other -> !other.getId().equals(character.getId())).map(this::toEntityView).toList();
        List<EntityView> monsterViews = zone.getMonsters().stream().map(this::toEntityView).toList();
        List<EntityView> npcViews = zone.getNpcs().stream().map(this::toEntityView).toList();

        List<PortalView> portals = zone.getPortals().stream().map(portal -> new PortalView(portal.position().x(),
                portal.position().y(), portal.direction(), portal.targetZone().getName())).toList();

        List<WaypointView> waypoints = remainingWaypoints().stream().map(p -> new WaypointView(p.x(), p.y())).toList();

        output.write("ViewAround", new Payload(zone.getName(), zone.getDescription(), self.x(), self.y(),
                characterViews, monsterViews, npcViews, portals, waypoints), false);
    }

    private EntityView toEntityView(AbstractCharacter other) {
        MovementEngine.ActiveMovement movement = other.activeMovement;
        Double targetX = null;
        Double targetY = null;
        if (movement != null && !movement.remainingWaypoints().isEmpty()) {
            List<Position> waypoints = movement.remainingWaypoints();
            Position destination = waypoints.get(waypoints.size() - 1);
            targetX = destination.x();
            targetY = destination.y();
        }
        return new EntityView(other.getId(), other.getName(), other.getPosition().x(), other.getPosition().y(),
                MovementEngine.unitsPerSecond(other.getSpeed()), other.getCurrentHealth(), other.getMaxHealth(),
                other.getLevel(), targetX, targetY);
    }

    private List<Position> remainingWaypoints() {
        MovementEngine.ActiveMovement movement = character.activeMovement;
        return movement == null ? List.of() : movement.remainingWaypoints();
    }
}
