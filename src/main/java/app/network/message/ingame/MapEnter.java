package app.network.message.ingame;

import java.util.List;

import app.domain.actor.AbstractCharacter;
import app.domain.map.Position;
import app.domain.world.MapInstance;
import app.game.engine.MovementEngine;
import app.network.OutputJsonMessage;
import app.network.server.tcpjson.TcpJsonOutput;

/**
 * Photo de la map elle-même envoyée au chargement (création/sélection de
 * personnage, portail, réapparition) — pas à chaque déplacement. Ne transmet
 * plus la liste des occupants (personnages/monstres/PNJ) : celle-ci arrive
 * séparément, scopée à AWARENESS_RANGE, via {@link EntityAppeared} poussé par
 * {@link app.domain.actor.KnownList#populate()} au moment du join qui précède
 * l'envoi de ce message (voir MapInstance.join).
 */
public record MapEnter(AbstractCharacter character) implements OutputJsonMessage {

    public record PortalView(double x, double y, String direction, String targetMapName) {
    }

    public record WaypointView(double x, double y) {
    }

    public record Payload(String mapName, String mapDescription, double selfX, double selfY, double selfHeading,
            List<PortalView> portals, List<WaypointView> remainingWaypoints) {
    }

    @Override
    public void toJson(TcpJsonOutput output) {
        MapInstance map = character.getMotionSystem().getCurrentMap();
        Position self = character.getMotionSystem().getPosition();

        List<PortalView> portals = map.getPortals().stream().map(portal -> new PortalView(portal.position().x(),
                portal.position().y(), portal.direction(), portal.targetMap().getName())).toList();

        List<WaypointView> waypoints = remainingWaypoints().stream().map(p -> new WaypointView(p.x(), p.y())).toList();

        output.write("MapEnter", new Payload(map.getName(), map.getDescription(), self.x(), self.y(),
                character.getMotionSystem().getHeading(), portals, waypoints));
    }

    private List<Position> remainingWaypoints() {
        MovementEngine.ActiveMovement movement = character.getMotionSystem().getActiveMovement();
        return movement == null ? List.of() : movement.remainingWaypoints();
    }
}
