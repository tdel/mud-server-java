package app.network.message.ingame;

import java.util.List;

import app.network.OutputJsonMessage;
import app.domain.actor.AbstractCharacter;
import app.domain.actor.AbstractNpc;
import app.domain.actor.instance.CharacterInstance;
import app.domain.actor.instance.MonsterInstance;
import app.domain.map.Position;
import app.domain.world.ZoneInstance;
import app.game.engine.MovementEngine;
import app.network.server.tcpjson.TcpJsonOutput;

public record ViewAround(AbstractCharacter character) implements OutputJsonMessage {

    public static final double VIEWPORT_RADIUS = 8.0;

    public record EntityView(String name, double x, double y) {
    }

    public record PortalNearbyView(double x, double y, String direction, String targetZoneName) {
    }

    public record WaypointView(double x, double y) {
    }

    public record Payload(String zoneName, String zoneDescription, double selfX, double selfY,
            List<EntityView> charactersNearby, List<EntityView> monstersNearby, List<EntityView> npcsNearby,
            List<PortalNearbyView> portalsNearby, List<WaypointView> remainingWaypoints) {
    }

    @Override
    public void toJson(TcpJsonOutput output) {
        ZoneInstance zone = character.getCurrentZone();
        Position self = character.getPosition();
        List<AbstractCharacter> nearby = zone.occupantsWithin(self, VIEWPORT_RADIUS);

        List<EntityView> characterViews = nearby.stream().filter(CharacterInstance.class::isInstance)
                .filter(other -> !other.getId().equals(character.getId())).map(this::toEntityView).toList();
        List<EntityView> monsterViews = nearby.stream().filter(MonsterInstance.class::isInstance)
                .map(this::toEntityView).toList();
        List<EntityView> npcViews = nearby.stream().filter(AbstractNpc.class::isInstance).map(this::toEntityView)
                .toList();

        List<PortalNearbyView> portalsNearby = zone.getPortals().stream()
                .filter(portal -> portal.position().distanceTo(self) <= VIEWPORT_RADIUS)
                .map(portal -> new PortalNearbyView(portal.position().x(), portal.position().y(), portal.direction(),
                        portal.targetZone().getName()))
                .toList();

        List<WaypointView> waypoints = remainingWaypoints().stream().map(p -> new WaypointView(p.x(), p.y())).toList();

        output.write("ViewAround", new Payload(zone.getName(), zone.getDescription(), self.x(), self.y(),
                characterViews, monsterViews, npcViews, portalsNearby, waypoints), false);
    }

    private EntityView toEntityView(AbstractCharacter other) {
        return new EntityView(other.getName(), other.getPosition().x(), other.getPosition().y());
    }

    private List<Position> remainingWaypoints() {
        MovementEngine.ActiveMovement movement = character.activeMovement;
        return movement == null ? List.of() : movement.remainingWaypoints();
    }
}
