package app.network.message.ingame;

import java.util.List;

import app.domain.actor.AbstractCharacter;
import app.domain.actor.AbstractNpc;
import app.domain.actor.instance.CharacterInstance;
import app.domain.actor.instance.MonsterInstance;
import app.domain.map.Position;
import app.domain.world.ZoneInstance;
import app.game.engine.MovementEngine;
import app.network.OutputJsonMessage;
import app.network.server.tcpjson.TcpJsonOutput;

/**
 * Photo de la zone envoyée au chargement (création/sélection de personnage,
 * portail, réapparition) — pas à chaque déplacement. Ne liste que ce qui est
 * déjà dans la KnownList du personnage (peuplée par ZoneInstance.join juste
 * avant l'envoi de ce message) ; les entités qui entrent/sortent de portée
 * ensuite sont signalées par EntityAppeared/EntityDisappeared.
 */
public record ZoneEnter(AbstractCharacter character) implements OutputJsonMessage {

    public record PortalView(double x, double y, String direction, String targetZoneName) {
    }

    public record WaypointView(double x, double y) {
    }

    public record Payload(String zoneName, String zoneDescription, double selfX, double selfY, double selfHeading,
            List<EntityView> characters, List<EntityView> monsters, List<EntityView> npcs, List<PortalView> portals,
            List<WaypointView> remainingWaypoints) {
    }

    @Override
    public void toJson(TcpJsonOutput output) {
        ZoneInstance zone = character.getCurrentZone();
        Position self = character.getPosition();
        List<AbstractCharacter> known = character.getKnownList().asList();

        List<EntityView> characterViews = known.stream().filter(CharacterInstance.class::isInstance).map(EntityView::of)
                .toList();
        List<EntityView> monsterViews = known.stream().filter(MonsterInstance.class::isInstance).map(EntityView::of)
                .toList();
        List<EntityView> npcViews = known.stream().filter(AbstractNpc.class::isInstance).map(EntityView::of).toList();

        List<PortalView> portals = zone.getPortals().stream().map(portal -> new PortalView(portal.position().x(),
                portal.position().y(), portal.direction(), portal.targetZone().getName())).toList();

        List<WaypointView> waypoints = remainingWaypoints().stream().map(p -> new WaypointView(p.x(), p.y())).toList();

        output.write("ZoneEnter", new Payload(zone.getName(), zone.getDescription(), self.x(), self.y(),
                character.getHeading(), characterViews, monsterViews, npcViews, portals, waypoints), false);
    }

    private List<Position> remainingWaypoints() {
        MovementEngine.ActiveMovement movement = character.activeMovement;
        return movement == null ? List.of() : movement.remainingWaypoints();
    }
}
