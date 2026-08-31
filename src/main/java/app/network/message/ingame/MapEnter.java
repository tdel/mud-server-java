package app.network.message.ingame;

import java.util.List;

import app.domain.actor.AbstractCharacter;
import app.domain.map.Position;
import app.domain.world.MapInstance;
import app.game.engine.MovementEngine;
import app.network.OutputJsonMessage;
import app.network.server.tcpjson.TcpJsonOutput;

/**
 * Photo de la map envoyée au chargement (création/sélection de personnage,
 * portail, réapparition) — pas à chaque déplacement. Liste TOUS les occupants
 * de la map (pas seulement la KnownList du personnage) : la sélection d'une
 * cible ne doit pas dépendre de la portée de perception, seules les diffusions
 * de mouvement/combat/chat en cours de partie restent scopées à la KnownList
 * pour la bande passante (voir AbstractCharacter.broadcastToMap).
 */
public record MapEnter(AbstractCharacter character) implements OutputJsonMessage {

    public record PortalView(double x, double y, String direction, String targetMapName) {
    }

    public record WaypointView(double x, double y) {
    }

    public record Payload(String mapName, String mapDescription, double selfX, double selfY, double selfHeading,
            List<EntityView> characters, List<EntityView> monsters, List<EntityView> npcs, List<PortalView> portals,
            List<WaypointView> remainingWaypoints) {
    }

    @Override
    public void toJson(TcpJsonOutput output) {
        MapInstance map = character.getCurrentMap();
        Position self = character.getPosition();

        List<EntityView> characterViews = map.characters().stream()
                .filter(other -> !other.getId().equals(character.getId())).map(EntityView::of).toList();
        List<EntityView> monsterViews = map.getMonsters().stream().map(EntityView::of).toList();
        List<EntityView> npcViews = map.getNpcs().stream().map(EntityView::of).toList();

        List<PortalView> portals = map.getPortals().stream().map(portal -> new PortalView(portal.position().x(),
                portal.position().y(), portal.direction(), portal.targetMap().getName())).toList();

        List<WaypointView> waypoints = remainingWaypoints().stream().map(p -> new WaypointView(p.x(), p.y())).toList();

        output.write("MapEnter", new Payload(map.getName(), map.getDescription(), self.x(), self.y(),
                character.getHeading(), characterViews, monsterViews, npcViews, portals, waypoints), false);
    }

    private List<Position> remainingWaypoints() {
        MovementEngine.ActiveMovement movement = character.getActiveMovement();
        return movement == null ? List.of() : movement.remainingWaypoints();
    }
}
